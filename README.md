# Vaterial Files

[本文中文版](README_zh-CN.md)

An open source Material Design file manager, for Android 13+.

## Preview

<p><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="32%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="32%" /></p>

## Features

- Open source: Lightweight, clean and secure.
- Material Design: Follows Material Design guidelines, with attention into details.
- Breadcrumbs: Navigate in the filesystem with ease.
- Root support: View and manage files with root access.
- Archive support: View, extract and create common compressed files.
- NAS support: View and manage files on FTP, SFTP, SMB and WebDAV servers.
- Themes: Customizable UI colors, plus night mode with optional true black.
- Display size: Four-level proportional scaling (large / medium / small / smaller) for all in-app content.
- App lock: Authenticate on startup and when returning from background, with PIN, fingerprint or system credential.
- Grid view: Configurable number of columns per row (2 to 7).
- List details: Directories show modification time and first-level item count on the second line, like files.
- Linux-aware: Like [Nautilus](https://apps.gnome.org/Nautilus/), knows symbolic links, file permissions and SELinux context.
- Robust: Uses Linux system calls under the hood, not yet another [`ls` parser](https://news.ycombinator.com/item?id=7994720).
- Well-implemented: Built upon the right things, including [Java NIO2 File API](https://docs.oracle.com/javase/8/docs/api/java/nio/file/package-summary.html) and [LiveData](https://developer.android.com/topic/libraries/architecture/livedata).

## Fork customizations

On top of all upstream features, this fork (based on [Material Files](https://github.com/zhanghai/MaterialFiles)) adds:

- **Material 3 & Material You**: Dynamic-color theming with a redesigned, collapsible Material 3 settings screen.
- **Four view modes**: List, dual panes, Finder-style **Columns** (Miller columns with cross-column drag & drop) and Grid.
- **Storage analysis (ncdu-style)**: One-tap recursive size analysis with per-item share bars and automatic re-sorting by real size, driven by a persistent state button in the breadcrumb bar with a configurable scope.
- **Media tools**: Video and image **info** pages and **re-encoding** — video via Media3 (codec, resolution, bitrate, frame rate, HDR→SDR), image between PNG / JPEG / HEIC (quality, rotation, resizing, EXIF preserved) — plus an **mpv-based video player** with brightness / volume / seek / speed gestures and HDR playback.
- **Iconified popup menus**: Overflow, view/sort and multi-selection menus as icon popup windows for one-tap access.

## License

    Copyright (C) 2018 Hai Zhang

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>.
