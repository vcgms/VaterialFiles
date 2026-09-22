package dev.vcgms.aapp.vaterialfiles.provider.common

import java.io.Closeable

interface CloseableIterator<T> : Iterator<T>, Closeable
