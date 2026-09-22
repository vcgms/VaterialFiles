package dev.vcgms.aapp.vaterialfiles.provider.webdav

import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.provider.webdav.client.Authority

fun Authority.createWebDavRootPath(): Path =
    WebDavFileSystemProvider.getOrNewFileSystem(this).rootDirectory
