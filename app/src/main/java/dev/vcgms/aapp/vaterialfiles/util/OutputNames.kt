package dev.vcgms.aapp.vaterialfiles.util

import java8.nio.file.Path
import dev.vcgms.aapp.vaterialfiles.provider.common.exists

// How the output file name is derived from the source file name.
enum class OutputNamingMode {
    KEEP, SUFFIX, CUSTOM
}

object OutputNames {

    fun resolvedName(
        baseName: String,
        extension: String,
        mode: OutputNamingMode,
        suffix: String,
        customName: String
    ): String = when (mode) {
        OutputNamingMode.KEEP -> baseName + extension
        OutputNamingMode.SUFFIX -> baseName + suffix + extension
        OutputNamingMode.CUSTOM -> customName
    }

    // Returns a path for the given output name that does not collide with an existing file,
    // appending _1, _2, ... before the extension as needed. When the caller intends to overwrite
    // the source in place (delete-source mode with an identical name) the source path is returned
    // unchanged so the export replaces it instead of being renamed away from it.
    fun resolveUnique(parent: Path, name: String, source: Path?): Path {
        val first = parent.resolve(name)
        if (source != null && first == source) {
            return first
        }
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val extension = if (dot > 0) name.substring(dot) else ""
        var candidate = first
        var index = 1
        while (candidate.exists()) {
            candidate = parent.resolve(base + "_" + index + extension)
            index++
        }
        return candidate
    }
}
