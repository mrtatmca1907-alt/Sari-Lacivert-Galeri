package com.sarilacivert.galeri.data

internal object MediaCachePolicy {
    fun shouldReuse(hasCache: Boolean, dirty: Boolean): Boolean = hasCache && !dirty
}
