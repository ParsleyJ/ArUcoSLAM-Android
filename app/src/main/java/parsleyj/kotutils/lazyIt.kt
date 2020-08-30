package org.parsleyj.kotutils

class IteratorStateException(message: String) : RuntimeException(message)

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
                private var queue:Optional<T> = nothing()

                override fun hasNext(): Boolean {
                    if (queue.isNothing()) {
                        while (originalIt.hasNext()) {
                            val item = originalIt.next()
                            if (block(item)) {
                                queue = some(item)
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
                    if (q is OSome<T>) {
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

fun Iterable<String>.joinWithSeparator(sep: String):String{
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


fun Iterable<String>.prepend(what:String) = this.itMap { "$what$it" }

fun <T> Iterable<T>.mapToString() = this.itMap { "$it" }