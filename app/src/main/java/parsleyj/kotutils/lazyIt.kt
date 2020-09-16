package parsleyj.kotutils

class IteratorStateException(message: String) : RuntimeException(message)

interface NonEmptyIterable<T> : Iterable<T> {
    fun itFirst(): T {
        return iterator().next()
    }
}


/**
 * Generates a [NonEmptyIterable] by wrapping this [Iterable]. If an [Iterator] returned by this iterable is empty
 * (i.e. the first call to [Iterator.hasNext] returns false) the iterable returns a [ItSingleton.ItSingletonIterator]
 * which provides the default value instead.
 */
fun <T : Any> Iterable<T>.asNonEmpty(defaultValue: T): NonEmptyIterable<T> {
    return object : NonEmptyIterable<T> {
        override fun iterator(): Iterator<T> {
            val it = this@asNonEmpty.iterator()
            return if (it.hasNext()) {
                it
            } else {
                ItSingleton.ItSingletonIterator(defaultValue)
            }
        }
    }

}


class ItSingleton<T : Any>(private val t: T) : NonEmptyIterable<T> {

    class ItSingletonIterator<T : Any>(t: T) : Iterator<T> {
        private var wr: T? = t

        override fun hasNext(): Boolean {
            return wr != null
        }

        override fun next(): T {
            val r = wr
            if (r != null) {
                wr = null
                return r
            }
            throw IteratorStateException("Invalid state of iterator (called next() without hasNext()?)")
        }


    }

    override fun iterator(): Iterator<T> {
        return ItSingletonIterator(t)
    }

}

inline fun <T, R> Iterable<T>.itMap(crossinline block: (T) -> R): Iterable<R> {
    return object : Iterable<R> {
        override fun iterator(): Iterator<R> {
            val originalIt = this@itMap.iterator()
            return object : Iterator<R> {
                override fun hasNext() = originalIt.hasNext()

                override fun next() = block(originalIt.next())
            }
        }
    }
}


inline fun <reified T> Iterable<T?>.itExcludeNulls(): Iterable<T> {
    return this.itFilter { it != null }.map { it as T }
}


inline fun <T> Iterable<T>.itFilter(crossinline block: (T) -> Boolean): Iterable<T> {
    return object : Iterable<T> {
        override fun iterator(): Iterator<T> {
            val originalIt = this@itFilter.iterator()
            return object : Iterator<T> {
                private var queue: Maybe<T> = nothing()

                override fun hasNext(): Boolean {
                    if (queue.isNothing()) {
                        while (originalIt.hasNext()) {
                            val item = originalIt.next()
                            if (block(item)) {
                                queue = just(item)
                                return true
                            }
                        }
                        return false
                    } else {
                        return true
                    }
                }

                override fun next(): T {
                    val q = queue
                    if (q is MJust<T>) {
                        val r = q.get
                        queue = nothing()
                        return r
                    } else {
                        throw IteratorStateException("Invalid state of iterator (called next() without hasNext()?)")
                    }
                }
            }
        }
    }
}

fun <T> Iterable<T>.itTakeWhile(predicate: (T) -> Boolean): Iterable<T> {
    return object :Iterable<T>{
        override fun iterator(): Iterator<T> {
            val originalIt = this@itTakeWhile.iterator()
            return object : Iterator<T>{
                private var queue: Maybe<T> = nothing()

                override fun hasNext(): Boolean {
                    if(queue.isNothing()){
                        if(originalIt.hasNext()){
                            val item = originalIt.next()
                            if(predicate(item)){
                                queue = just(item)
                                return true
                            }
                        }
                        return false
                    }else{
                        return true
                    }
                }

                override fun next(): T {
                    val q = queue
                    if (q is MJust<T>) {
                        val r = q.get
                        queue = nothing()
                        return r
                    } else {
                        throw IteratorStateException("Invalid state of iterator (called next() without hasNext()?)")
                    }
                }

            }
        }

    }
}


fun Iterable<String>.joinWithSeparator(sep: String): String {
    val iterator = this.iterator()
    if (!iterator.hasNext()) {
        return ""
    }
    var accumulator: String = iterator.next()
    while (iterator.hasNext()) {
        accumulator = "$accumulator$sep${iterator.next()}"
    }
    return accumulator
}


fun Iterable<String>.prepend(what: String) = this.itMap { "$what$it" }

fun <T> Iterable<T>.mapToString() = this.itMap { "$it" }

