package parsleyj.kotutils


class MutableOptional<T>(nullable: T?) : MutableIterable<T> {
    companion object {
        fun <T> empty() = MutableOptional<T>(null)
        fun <T> wrap(nullable: T) = MutableOptional(nullable)
    }

    private var wrappedVar = nullable

    override fun iterator(): MutableIterator<T> {
        return object : MutableIterator<T> {
            override fun hasNext() = wrappedVar != null

            override fun next() = wrappedVar!!

            override fun remove() {
                wrappedVar = null
            }
        }
    }

    fun mutFilter(predicate: (T) -> Boolean): MutableOptional<T> {
        if (this.matches(predicate)) {
            wrappedVar = null
        }
        return this
    }

    fun mutMap(mapper: (T) -> T?): MutableOptional<T> {
        wrappedVar = wrappedVar?.let(mapper)
        return this
    }

    fun mutAssign(supply: ()-> T?): MutableOptional<T> {
        wrappedVar = supply()
        return this
    }

    fun peek(action: (T) -> Unit): MutableOptional<T> {
        wrappedVar?.let(action)
        return this
    }


    fun matches(predicate: (T) -> Boolean): Boolean {
        return wrappedVar?.let { predicate(it) } == true
    }

    fun isPresent() = wrappedVar != null

    fun get() = wrappedVar


}