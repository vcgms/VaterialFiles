package dev.vcgms.aapp.vaterialfiles.videoinfo

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.Menu
import android.view.MenuItem
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dev.vcgms.aapp.vaterialfiles.R
import dev.vcgms.aapp.vaterialfiles.app.AppActivity
import dev.vcgms.aapp.vaterialfiles.databinding.VideoInfoActivityBinding
import dev.vcgms.aapp.vaterialfiles.file.FileItem
import dev.vcgms.aapp.vaterialfiles.file.fileSize
import dev.vcgms.aapp.vaterialfiles.file.format
import dev.vcgms.aapp.vaterialfiles.filelist.name
import dev.vcgms.aapp.vaterialfiles.util.getColorByAttr
import dev.vcgms.aapp.vaterialfiles.util.layoutInflater
import dev.vcgms.aapp.vaterialfiles.util.setForceShowIcons
import dev.vcgms.aapp.vaterialfiles.videoedit.startVideoTranscodeActivity

// Shows container-level and per-track (video/audio stream) information of a video file, with a
// re-encode action at the bottom.
class VideoInfoActivity : AppActivity() {
    private lateinit var binding: VideoInfoActivityBinding

    private var file: FileItem? = null

    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = VideoInfoActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            title = getString(R.string.video_info_title)
            setDisplayHomeAsUpEnabled(true)
        }
        val file = intent.getParcelableExtra(EXTRA_FILE, FileItem::class.java)
        if (file == null) {
            finish()
            return
        }
        this.file = file
        loadVideoInfo(file)
    }

    override fun onDestroy() {
        super.onDestroy()
        loadJob?.cancel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.video_info, menu)
        menu.setForceShowIcons()
        return super.onCreateOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_reencode -> {
            file?.let { startVideoTranscodeActivity(this, it) }
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    private fun loadVideoInfo(file: FileItem) {
        binding.progress.isVisible = true
        binding.errorText.isVisible = false
        binding.scrollView.isVisible = false
        binding.container.removeAllViews()
        loadJob = lifecycleScope.launch {
            val info = try {
                withContext(Dispatchers.IO) { VideoInfoLoader.load(file.path) }
            } catch (e: Exception) {
                binding.progress.isVisible = false
                binding.errorText.isVisible = true
                return@launch
            }
            binding.progress.isVisible = false
            binding.scrollView.isVisible = true
            renderInfo(file, info)
        }
    }

    private fun renderInfo(file: FileItem, info: VideoInfo) {
        val container = binding.container
        container.removeAllViews()
        addSectionTitle(R.string.video_info_section_file)
        addItem(R.string.video_info_name, file.name)
        val attributes = file.attributes
        if (!attributes.isDirectory) {
            addItem(R.string.video_info_size, attributes.fileSize.formatHumanReadable(this))
        }
        info.durationMs?.let {
            addItem(R.string.video_info_duration, Duration.ofMillis(it).format())
        }
        info.containerMime?.let {
            addItem(R.string.video_info_container, it)
        }
        info.overallBitRate?.let {
            addItem(R.string.video_info_overall_bit_rate, formatBitRate(it))
        }
        if (info.videoTracks.isNotEmpty()) {
            addSectionTitle(R.string.video_info_section_video_tracks)
            for ((index, track) in info.videoTracks.withIndex()) {
                if (info.videoTracks.size > 1) {
                    addTrackSubTitle(getString(R.string.video_info_track_number_format, index + 1))
                }
                addItem(R.string.video_info_track_format, mimeDisplayName(track.mime))
                if (track.width != null && track.height != null) {
                    addItem(
                        R.string.video_info_resolution, getString(
                            R.string.video_info_resolution_format, track.width, track.height
                        )
                    )
                }
                track.frameRate?.let {
                    addItem(
                        R.string.video_info_frame_rate, getString(
                            R.string.video_info_fps_format,
                            String.format(Locale.US, "%.2f", it)
                        )
                    )
                }
                track.bitRate?.let {
                    addItem(R.string.video_info_bit_rate, formatBitRate(it))
                }
                track.profile?.let {
                    addItem(R.string.video_info_profile, it)
                }
                if (track.rotation != null && track.rotation != 0) {
                    addItem(R.string.video_info_rotation, getString(
                        R.string.video_info_degrees_format, track.rotation
                    ))
                }
                track.colorTransfer?.let {
                    addItem(R.string.video_info_color_transfer, it)
                }
                track.colorStandard?.let {
                    addItem(R.string.video_info_color_standard, it)
                }
                track.iFrameInterval?.let {
                    addItem(R.string.video_info_key_frame_interval, getString(
                        R.string.video_info_seconds_format, it
                    ))
                }
            }
        }
        if (info.audioTracks.isNotEmpty()) {
            addSectionTitle(R.string.video_info_section_audio_tracks)
            for ((index, track) in info.audioTracks.withIndex()) {
                if (info.audioTracks.size > 1) {
                    addTrackSubTitle(getString(R.string.video_info_track_number_format, index + 1))
                }
                addItem(R.string.video_info_track_format, mimeDisplayName(track.mime))
                track.sampleRate?.let {
                    addItem(R.string.video_info_sample_rate, getString(
                        R.string.video_info_hz_format, it
                    ))
                }
                track.channelCount?.let {
                    addItem(R.string.video_info_channel_count, it.toString())
                }
                track.bitRate?.let {
                    addItem(R.string.video_info_bit_rate, formatBitRate(it))
                }
                track.profile?.let {
                    addItem(R.string.video_info_profile, it)
                }
            }
        }
    }

    private fun addSectionTitle(@StringRes titleRes: Int) {
        val titleView = TextView(this).apply {
            text = getString(titleRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(getColorByAttr(android.R.attr.colorPrimary))
            setPadding(0, (16 * resources.displayMetrics.density).toInt(), 0, (4 * resources.displayMetrics.density).toInt())
        }
        binding.container.addView(titleView)
    }

    private fun addTrackSubTitle(title: String) {
        val titleView = TextView(this).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getColorByAttr(android.R.attr.textColorSecondary))
            setPadding(0, (8 * resources.displayMetrics.density).toInt(), 0, 0)
        }
        binding.container.addView(titleView)
    }

    private fun addItem(@StringRes labelRes: Int, value: String) {
        val density = resources.displayMetrics.density
        val labelView = TextView(this).apply {
            text = getString(labelRes)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(getColorByAttr(android.R.attr.textColorSecondary))
        }
        val valueView = TextView(this).apply {
            text = value
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(getColorByAttr(android.R.attr.textColorPrimary))
            setTextIsSelectable(true)
        }
        binding.container.addView(
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, (8 * density).toInt(), 0, (8 * density).toInt())
                addView(labelView)
                addView(valueView)
            }
        )
    }

    private fun formatBitRate(bitsPerSecond: Long): String = when {
        bitsPerSecond >= 1_000_000 ->
            String.format(Locale.US, "%.2f Mbps", bitsPerSecond / 1_000_000.0)
        else ->
            String.format(Locale.US, "%.0f kbps", bitsPerSecond / 1000.0)
    }
}

private const val EXTRA_FILE = "dev.vcgms.aapp.vaterialfiles.videoinfo.extra.FILE"

fun startVideoInfoActivity(context: Context, file: FileItem) {
    val intent = Intent(context, VideoInfoActivity::class.java)
    intent.putExtra(EXTRA_FILE, file)
    context.startActivity(intent)
}
