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

- **Four view modes**: List / Dual panes / **Columns** / Grid. Columns is a Finder-style Miller columns navigation with cross-column drag & drop; dual panes offer move/copy to the other pane.
- **Storage analysis (ncdu-style)**: Analyze the current directory in one tap — every item shows its recursive size and share of the parent, the item background fills as a bar visualizing the share, and the list re-sorts by real size once done; works in list, dual-pane and column views.
- **Storage analysis state button**: A persistent state indicator at the start of the breadcrumb bar (turned off / not analyzed / analyzing / done, animated while analyzing). Tap to force-analyze the current directory or stop the analysis; long press turns the feature off; every action is acknowledged with a toast.
- **Controllable analysis scope**: Limit analysis to subdirectories of user storage (on by default, keeps the whole user space from being scanned) and optionally allow analyzing remote directories (off by default).
- **Popup menus**: The overflow, view/sort and selection menus are iconified popup windows for one-tap access.
- **Redesigned settings**: Collapsible Material 3 categories (collapsed by default), including a dedicated "Storage analysis" category.
- **Display long file name**: A new "multiple lines" mode shows the full file name with adaptive item height.
- **User storage friendly**: The user storage root reads "User storage" in the toolbar title and breadcrumbs, other users read "User 999 storage".
- **Polish**: Toolbar title, breadcrumbs and list titles align on one axis; iconified navigation button and storage analysis indicator; app name shown atop the navigation drawer; comfortable spacing between subtitle icons and counts.

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
