package dev.vcgms.aapp.vaterialfiles.filelist

// Number of columns per row in grid view, configurable from 2 to 7 (default FIVE).
enum class GridColumnCount(val count: Int) {
    TWO(2),
    THREE(3),
    FOUR(4),
    FIVE(5),
    SIX(6),
    SEVEN(7)
}
