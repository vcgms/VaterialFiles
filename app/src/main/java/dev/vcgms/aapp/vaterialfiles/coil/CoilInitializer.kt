package dev.vcgms.aapp.vaterialfiles.coil

import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.decode.BitmapFactoryDecoder
import coil3.decode.ExifOrientationStrategy
import coil3.gif.AnimatedImageDecoder
import coil3.svg.SvgDecoder
import com.awxkee.jxlcoder.coil.JxlDecoder
import dev.vcgms.aapp.vaterialfiles.app.application

fun initializeCoil() {
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .components {
                add(AppIconApplicationInfoKeyer())
                add(AppIconApplicationInfoFetcherFactory(application))
                add(AppIconPackageNameKeyer())
                add(AppIconPackageNameFetcherFactory(application))
                add(PathAttributesKeyer())
                add(PathAttributesFetcher.Factory(application))
                add(AnimatedImageDecoder.Factory())
                add(SvgDecoder.Factory())
                // JPEG XL through the official Coil 3 plugin (16 KB aligned native libraries).
                add(JxlDecoder.Factory())
                // DNG (RAW) through the platform ImageDecoder (supported since API 33).
                add(DngDecoder.Factory())
                // Force the BitmapFactory-based decoder with RESPECT_ALL for all remaining static
                // images. Coil's default (StaticImageDecoder, backed by ImageDecoder) applies the
                // HEIF container transform but not the EXIF Orientation tag, so HEIC photos whose
                // rotation lives in EXIF render un-rotated here while the system thumbnail (which
                // does apply it) looks correct. BitmapFactoryDecoder + RESPECT_ALL decodes raw and
                // applies EXIF orientation for every format, matching the re-encode pipeline.
                // Registered after the specialized decoders so those keep priority for their types,
                // and before Coil's auto-added Android decoders so this one wins for static images.
                add(
                    BitmapFactoryDecoder.Factory(
                        exifOrientationStrategy = ExifOrientationStrategy.RESPECT_ALL
                    )
                )
            }
            .build()
    }
}
