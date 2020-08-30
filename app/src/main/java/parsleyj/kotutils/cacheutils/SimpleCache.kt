package parsleyj.kotutils.cacheutils

/**
 * Created on 15/11/2019.
 *
 */
class SimpleCache : Cache {
    private val cache = mutableMapOf<Int, Any?>()

    override fun <R : Any?> cached(f: () -> R) = internal(f, listOf(this))

    override fun <R> cached(vararg additionalKeys: Any, f: () -> R) = internal(
        f,
        listOf(this) + additionalKeys.toList()
    )

    private fun <R> internal(f: () -> R, k: List<Any>? = null): R {
        val internalK: Any = if (k != null) {
            f to k
        } else {
            f
        }
        return if (!cache.containsKey(internalK)) {
            val res = f()
            cache[k.hashCode()] = res
            res
        } else {
            @Suppress("UNCHECKED_CAST")
            cache[k.hashCode()]!! as R
        }

    }
}
