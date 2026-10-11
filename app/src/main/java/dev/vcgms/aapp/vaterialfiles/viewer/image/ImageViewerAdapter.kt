package dev.vcgms.aapp.vaterialfiles.viewer.image

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.BitmapFactory
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.coil.dispose
import dev.vcgms.aapp.vaterialfiles.coil.load
import coil3.size.Size
import com.davemorrissey.labs.subscaleview.ImageSource
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView.DefaultOnImageEventListener
import java.io.File
import java8.nio.file.Path
import java8.nio.file.Paths
import java8.nio.file.attribute.BasicFileAttributes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.vcgms.aapp.vaterialfiles.databinding.ImageViewerItemBinding
import dev.vcgms.aapp.vaterialfiles.file.MimeType
import dev.vcgms.aapp.vaterialfiles.file.asMimeType
import dev.vcgms.aapp.vaterialfiles.file.asMimeTypeOrNull
import dev.vcgms.aapp.vaterialfiles.file.fileProviderUri
import dev.vcgms.aapp.vaterialfiles.filelist.name
import dev.vcgms.aapp.vaterialfiles.provider.common.AndroidFileTypeDetector
import dev.vcgms.aapp.vaterialfiles.provider.common.newInputStream
import dev.vcgms.aapp.vaterialfiles.provider.common.readAttributes
import dev.vcgms.aapp.vaterialfiles.ui.SimpleAdapter
import dev.vcgms.aapp.vaterialfiles.util.fadeInUnsafe
import dev.vcgms.aapp.vaterialfiles.util.fadeOutUnsafe
import dev.vcgms.aapp.vaterialfiles.util.layoutInflater
import dev.vcgms.aapp.vaterialfiles.util.shortAnimTime
import dev.vcgms.aapp.vaterialfiles.util.showToast
import dev.vcgms.aapp.vaterialfiles.viewer.video.VideoViewerActivity
import kotlin.math.max

class ImageViewerAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val listener: (View) -> Unit
) : SimpleAdapter<Path, ImageViewerAdapter.ViewHolder>() {
    override val hasStableIds: Boolean
        get() = true

    override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ImageViewerItemBinding.inflate(parent.context.layoutInflater, parent, false))

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val path = getItem(position)
        val binding = holder.binding
        binding.image.setOnPhotoTapListener { view, _, _ -> listener(view) }
        binding.largeImage.setOnClickListener(listener)
        binding.hdrImage.onTapListener = listener
        loadImage(binding, path)
    }

    override fun onViewRecycled(holder: ViewHolder) {
        super.onViewRecycled(holder)

        val binding = holder.binding
        binding.image.dispose()
        binding.largeImage.recycle()
        // Drop the full-size HDR bitmap (if any) so it can be garbage collected.
        binding.hdrImage.setImageDrawable(null)
    }

    private fun loadImage(binding: ImageViewerItemBinding, path: Path) {
        binding.progress.fadeInUnsafe(true)
        binding.errorText.fadeOutUnsafe()
        binding.image.isVisible = false
        binding.largeImage.isVisible = false
        binding.hdrImage.isVisible = false
        binding.motionPhotoButton.isVisible = false
        lifecycleOwner.lifecycleScope.launch {
            val headerInfo = try {
                withContext(Dispatchers.IO) { HeaderInfo(path.isMotionPhoto(), path.hasHdrGainmap()) }
            } catch (e: Exception) {
                HeaderInfo(false, false)
            }
            val imageInfo = try {
                withContext(Dispatchers.IO) { path.loadImageInfo() }
            } catch (e: Exception) {
                e.printStackTrace()
                showError(binding, e)
                return@launch
            }
            if (headerInfo.isMotionPhoto) {
                binding.motionPhotoButton.isVisible = true
                binding.motionPhotoButton.setOnClickListener {
                    playMotionPhoto(binding, path)
                }
            }
            if (headerInfo.isHdr) {
                setHdrColorMode(binding, true)
                binding.hdrImage.isVisible = true
                binding.hdrImage.onSoftwareFallbackListener = {
                    binding.root.context.showToast(R.string.image_viewer_software_fallback)
                }
                binding.hdrImage.setImagePath(
                    path,
                    onReady = {
                        binding.progress.fadeOutUnsafe()
                    },
                    onError = { e ->
                        e.printStackTrace()
                        binding.hdrImage.isVisible = false
                        setHdrColorMode(binding, false)
                        loadImageWithInfo(binding, path, imageInfo)
                    }
                )
            } else {
                setHdrColorMode(binding, false)
                loadImageWithInfo(binding, path, imageInfo)
            }
        }
    }

    private fun playMotionPhoto(binding: ImageViewerItemBinding, path: Path) {
        lifecycleOwner.lifecycleScope.launch {
            val mp4 = withContext(Dispatchers.IO) {
                val cacheDir = File(binding.root.context.cacheDir, "motion_photos")
                cacheDir.mkdirs()
                path.extractMotionPhotoMp4(cacheDir)
            }
            if (mp4 == null) {
                return@launch
            }
            val intent = Intent(binding.root.context, VideoViewerActivity::class.java)
            VideoViewerActivity.putExtras(intent, listOf(Paths.get(mp4.absolutePath)), 0)
            binding.root.context.startActivity(intent)
        }
    }

    private fun setHdrColorMode(binding: ImageViewerItemBinding, hdr: Boolean) {
        val activity = binding.root.context as? Activity ?: return
        activity.window.setColorMode(
            if (hdr) ActivityInfo.COLOR_MODE_HDR else ActivityInfo.COLOR_MODE_WIDE_COLOR_GAMUT
        )
    }

    private fun Path.loadImageInfo(): ImageInfo {
        val attributes = readAttributes(BasicFileAttributes::class.java)
        val mimeType = AndroidFileTypeDetector.getMimeType(this, attributes).asMimeType()
        val bitmapOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        newInputStream().use { BitmapFactory.decodeStream(it, null, bitmapOptions) }
        // Read the EXIF orientation ourselves: SubsamplingScaleImageView's ORIENTATION_USE_EXIF
        // relies on ContentResolver.getType() returning a recognized mime for the file-provider
        // URI, which is guessed from the extension and can be missing/unrecognized, silently
        // leaving large images un-rotated.
        val exifOrientation = try {
            newInputStream().buffered().use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            }
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        return ImageInfo(
            attributes, bitmapOptions.outWidth, bitmapOptions.outHeight,
            bitmapOptions.outMimeType?.asMimeTypeOrNull() ?: mimeType, exifOrientation
        )
    }

    private fun loadImageWithInfo(
        binding: ImageViewerItemBinding,
        path: Path,
        imageInfo: ImageInfo
    ) {
        if (!imageInfo.shouldUseLargeImageView) {
            binding.image.apply {
                isVisible = true
                // Fade the view itself instead of using Coil's crossfade transition: the
                // CrossfadeDrawable wrapper reports the placeholder's intrinsic size, which
                // corrupts PhotoViewAttacher's matrix/display-rect computation (min scale stuck
                // at 1 and taps falling outside the collapsed rect) when the transition's end
                // replacement does not run.
                alpha = 0f
                load(path to imageInfo.attributes) {
                    size(Size.ORIGINAL)
                    listener(
                        onSuccess = { _, _ ->
                            binding.progress.fadeOutUnsafe()
                            animate().alpha(1f).setDuration(context.shortAnimTime.toLong()).start()
                        },
                        onError = { _, result ->
                            showError(binding, result.throwable)
                        }
                    )
                }
            }
        } else {
            binding.largeImage.apply {
                setDoubleTapZoomDuration(300)
                orientation = exifOrientationToDegrees(imageInfo.exifOrientation)
                // Otherwise OnImageEventListener.onReady() is never called.
                isVisible = true
                alpha = 0f
                setOnImageEventListener(object : DefaultOnImageEventListener() {
                    override fun onReady() {
                        setDoubleTapZoomScale(binding.largeImage.cropScale)
                        binding.progress.fadeOutUnsafe()
                        binding.largeImage.fadeInUnsafe(true)
                    }

                    override fun onImageLoadError(e: Exception) {
                        e.printStackTrace()
                        showError(binding, e)
                    }
                })
                setImageRestoringSavedState(ImageSource.uri(path.fileProviderUri))
            }
        }
    }

    // Maps an EXIF orientation to the rotation degrees SubsamplingScaleImageView accepts. The
    // mirror/flip orientations are rare and cannot be expressed as a rotation, so they fall back
    // to no rotation.
    private fun exifOrientationToDegrees(orientation: Int): Int = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE ->
            SubsamplingScaleImageView.ORIENTATION_90
        ExifInterface.ORIENTATION_ROTATE_180 ->
            SubsamplingScaleImageView.ORIENTATION_180
        ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE ->
            SubsamplingScaleImageView.ORIENTATION_270
        else -> SubsamplingScaleImageView.ORIENTATION_0
    }

    private val ImageInfo.shouldUseLargeImageView: Boolean
        get() {
            // See BitmapFactory.cpp encodedFormatToString()
            if (mimeType == MimeType.IMAGE_GIF) {
                return false
            }
            if (width <= 0 || height <= 0) {
                return false
            }
            // 4 bytes per pixel for ARGB_8888.
            if (width * height * 4 > MAX_BITMAP_SIZE) {
                return true
            }
            if (width > 2048 || height > 2048) {
                val ratio = width.toFloat() / height
                if (ratio < 0.5 || ratio > 2) {
                    return true
                }
            }
            return false
        }

    private val SubsamplingScaleImageView.cropScale: Float
        get() {
            val viewWidth = (width - paddingLeft - paddingRight)
            val viewHeight = (height - paddingTop - paddingBottom)
            val orientation = appliedOrientation
            val rotated90Or270 = orientation == SubsamplingScaleImageView.ORIENTATION_90
                || orientation == SubsamplingScaleImageView.ORIENTATION_270
            val imageWidth = if (rotated90Or270) sHeight else sWidth
            val imageHeight = if (rotated90Or270) sWidth else sHeight
            return max(viewWidth.toFloat() / imageWidth, viewHeight.toFloat() / imageHeight)
        }

    private fun showError(binding: ImageViewerItemBinding, throwable: Throwable) {
        binding.progress.fadeOutUnsafe()
        binding.errorText.text = throwable.toString()
        binding.errorText.fadeInUnsafe(true)
        binding.image.isVisible = false
        binding.largeImage.isVisible = false
    }

    companion object {
        // @see android.graphics.RecordingCanvas#MAX_BITMAP_SIZE
        private const val MAX_BITMAP_SIZE = 100 * 1024 * 1024
    }

    class ViewHolder(val binding: ImageViewerItemBinding) : RecyclerView.ViewHolder(binding.root)

    private class ImageInfo(
        val attributes: BasicFileAttributes,
        val width: Int,
        val height: Int,
        val mimeType: MimeType,
        val exifOrientation: Int
    )

    private class HeaderInfo(
        val isMotionPhoto: Boolean,
        val isHdr: Boolean
    )
}
