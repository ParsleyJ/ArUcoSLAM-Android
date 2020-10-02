package parsleyj.kotutils.cacheutils

/**
 * Created on 15/11/2019.
 *
 */
@DslMarker
annotation class CacheKeyword



/**
 * Utilty used to design data structures with immutable properties which are costly to compute in
 * time but for which the result is not a problem in terms of memory (e.g. an immutable generic
 * matrix and its determinant).
 *
 * @sample cacheMatrixSampleKDOC
 *
 */
interface Cache {
    /**
     * If the function f has been never invoked, invokes the function and stores the result in memory.
     * Such result is then returned by all subsequent calls to this method with the same f argument.
     */
    @CacheKeyword
    fun <R : Any?> cached(f: () -> R): R

    /**
     * If the function f with the same additionalKeys has been never invoked, invokes the function and stores
     * the result in memory.
     * Such result is then returned by all subsequent calls to this method with the same f and additionalKeys
     * arguments.
     */
    @CacheKeyword
    fun <R : Any?> cached(vararg additionalKeys: Any, f: () -> R): R
}

fun cacheMatrixSampleKDOC() {
    class Matrix : Cache by SimpleCache() {
        fun determinant() = cached {
            // ...compute determinant...
            // the result is returned but is also stored,
            // so subsequent calls simply return the stored value
        }
    }
}