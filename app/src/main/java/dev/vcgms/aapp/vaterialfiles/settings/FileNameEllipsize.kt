package dev.vcgms.aapp.vaterialfiles.settings

import android.text.TextUtils

// How long file names are displayed in the list. MULTILINE keeps the full name across multiple
// lines (list layout only); the other values map to the corresponding truncation modes. The
// ordinal order must stay aligned with the persisted preference values.
enum class FileNameEllipsize(val truncateAt: TextUtils.TruncateAt?) {
    START(TextUtils.TruncateAt.START),
    MIDDLE(TextUtils.TruncateAt.MIDDLE),
    END(TextUtils.TruncateAt.END),
    MARQUEE(TextUtils.TruncateAt.MARQUEE),
    MULTILINE(null)
}
