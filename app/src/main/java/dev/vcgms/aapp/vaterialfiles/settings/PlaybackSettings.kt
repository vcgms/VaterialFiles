package dev.vcgms.aapp.vaterialfiles.settings

// The playback speed applied while the video area is held down.
enum class VideoPlayerLongPressSpeed(val speed: Double) {
    X2(2.0),
    X3(3.0),
}

// The image format of screenshots taken in the video player.
enum class ScreenshotFormat(val extension: String) {
    JPG(".jpg"),
    PNG(".png"),
}
