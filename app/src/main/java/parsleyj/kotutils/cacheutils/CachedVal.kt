package parsleyj.kotutils.cacheutils

import kotlin.reflect.KProperty

class CachedVal<T>(private val block:()->T) {
    operator fun getValue(thisRef: Cache, property: KProperty<*>) = thisRef.cached { block() }
}