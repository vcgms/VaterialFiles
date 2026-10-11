package dev.vcgms.aapp.vaterialfiles.util

import android.annotation.SuppressLint
import android.view.Menu
import androidx.appcompat.view.menu.MenuBuilder

// Toolbar overflow popups hide item icons by default; MenuBuilder exposes the toggle that makes
// them render. Call this after inflating a menu into a toolbar set as the support action bar.
@SuppressLint("RestrictedApi")
fun Menu.setForceShowIcons() {
    (this as? MenuBuilder)?.setOptionalIconsVisible(true)
}
