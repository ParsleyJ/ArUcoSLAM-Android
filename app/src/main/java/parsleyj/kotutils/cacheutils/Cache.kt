package parsleyj.kotutils.cacheutils

/**
 * Created on 15/11/2019.
 *
 */
@DslMarker
annotation class CacheKeyword

interface Cache{
    /**
     * If the function f has been never invoked, invokes the function and stores the result in memory.
     * Such result is then returned by all subsequent calls to this method with the same f argument.
     */
    @CacheKeyword
    fun <R : Any?> cached(f: ()->R) : R

    /**
     * If the function f with the same additionalKeys has been never invoked, invokes the function and stores
     * the result in memory.
     * Such result is then returned by all subsequent calls to this method with the same f and additionalKeys
     * arguments.
     */
    @CacheKeyword
    fun <R : Any?> cached(vararg additionalKeys: Any, f: ()->R) : R

    
}