# Vaterial Files

一个开源的 Material Design 文件管理器，适用于 Android 13+。

## 预览

<p><img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width="32%" />
<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width="32%" /> <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width="32%" /></p>

## 特性

- 开源：轻量、简洁并且安全。
- Material Design：遵循 Material Design 规范，并且注重细节。
- 面包屑导航栏：点击导航栏所显示路径中的任一文件夹即可快速访问。
- Root 支持：使用 root 权限查看和管理文件。
- 压缩文件支持：查看、提取和创建常见的压缩文件。
- NAS 支持：查看和管理 FTP、SFTP、SMB 和 WebDAV 服务器上的文件。
- 主题：可定制的界面颜色，以及可选纯黑的夜间模式。
- 显示大小：四档全局等比缩放（大 / 中 / 小 / 更小），作用于应用内全部内容。
- 应用锁：启动与从后台恢复时要求身份验证，支持 PIN、指纹或系统凭据。
- 网格视图：每行列数可配置（2 至 7 列）。
- 列表详情：目录与文件一样在第二行显示修改时间，并以一级子项目数代替大小。
- Linux 友好：类似 [Nautilus](https://wiki.gnome.org/action/show/Apps/Files)，支持符号链接、文件权限和 SELinux 上下文。
- 健壮性：使用 Linux 系统调用实现，而不是另一个 [`ls` 解析器](https://news.ycombinator.com/item?id=7994720)。
- 实现良好：使用正确的方式打造，包括 [Java NIO2 文件 API](https://docs.oracle.com/javase/8/docs/api/java/nio/file/package-summary.html) 和 [LiveData](https://developer.android.com/topic/libraries/architecture/livedata)。

## 本 fork 定制特性

在保留上游全部能力的基础上，本 fork（基于 [Material Files](https://github.com/zhanghai/MaterialFiles)）新增：

- **四种视图模式**：列表 / 双列 / **分栏** / 网格。分栏为仿访达（Finder）的 Miller columns 列式导航，支持跨列拖拽移动与复制；双列支持对另一列移动/复制。
- **存储分析（ncdu 式）**：一键分析当前目录——每个子项显示递归大小与所占父目录百分比，项背景以条形图直观呈现占比，分析完成后按实际大小自动排序；列表、双列、分栏三种视图均可用。
- **存储分析状态按钮**：地址栏左侧常驻状态图标（未启用 / 未分析 / 分析中 / 已完成四态，计算中有动画）。短按立即分析当前目录或停止分析，长按关闭功能；每次操作都有 toast 回执。
- **存储分析可控**：可在设置中限制为「仅分析用户存储的子目录」（默认开启，避免扫描整个用户空间），并单独决定是否「分析远程目录」（默认关闭）。
- **弹出式菜单**：顶栏溢出、视图/排序、多选菜单全部图标化弹窗化，排序与视图切换一步直达。
- **设置页重设计**：可折叠的 Material 3 风格分类，默认全部折叠；含独立「存储分析」分类。
- **显示长文件名**：新增「多行」模式，列表项自适应高度完整显示文件名。
- **用户存储友好**：用户存储根目录在标题与地址栏显示为「用户存储」，其他用户显示「用户999存储」。
- **细节打磨**：顶栏标题、地址栏与列表标题三线对齐；导航按钮与存储分析指示器图标化；抽屉顶部显示应用名；副标题图标与计数留白舒适。

## 许可证

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
