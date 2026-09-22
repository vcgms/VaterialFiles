package dev.vcgms.aapp.vaterialfiles.provider.common

import java8.nio.file.Path
import java.io.IOException

interface Searchable {
    @Throws(IOException::class)
    fun search(directory: Path, query: String, intervalMillis: Long, listener: (List<Path>) -> Unit)
}
