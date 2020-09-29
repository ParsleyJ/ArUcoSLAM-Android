package parsleyj.arucoslam.datamodel



/**
 * A [List] of [Double]s, backed by an array, to which new elements can be added and elements
 * belonging to the list can be replaced but cannot be removed. In other words, the number
 * of elements in the list (i.e. the [size]) can only grow or stay the same over time.
 * This constraint allows to store elements inside a contiguous array of [Double]s.
 * Moreover, the backing array is directly accessible via [elementData] and the data
 * inside it is always consistent with the data represented by this list.
 * For this reason the backing array grows each time an "add" operation is performed;
 * in this regard, please be careful by optimizing using [addFromArray] and [addAll] when adding
 * multiple elements.
 */
class MonotonicDoubleList(size: Int, init: (Int) -> Double) : List<Double> {
    companion object {
        const val DEFAULT_CAPACITY = 10
        private const val MAX_ARRAY_SIZE = Int.MAX_VALUE - 8

        fun copyOf(original: DoubleArray, newLength: Int): DoubleArray {
            val copy = DoubleArray(newLength)
            System.arraycopy(original, 0, copy, 0, original.size.coerceAtMost(newLength))
            return copy
        }
    }

    constructor(collection: List<Double>) : this(collection.size, collection::get)

    var elementData = DoubleArray(size, init)
        private set

    override val size: Int
        get() = elementData.size


    fun growBy(byHowMuch: Int) {
        val oldCapacity: Int = elementData.size
        var newCapacity = oldCapacity + byHowMuch
        if (newCapacity - MAX_ARRAY_SIZE > 0) {
            if (newCapacity < 0) throw OutOfMemoryError()
            newCapacity = if (newCapacity > MAX_ARRAY_SIZE) Int.MAX_VALUE else MAX_ARRAY_SIZE
        }
        elementData = copyOf(elementData, newCapacity)
    }

    override operator fun contains(element: Double) = indexOf(element) >= 0

    override fun containsAll(elements: Collection<Double>) = elements.all { it in this }

    override fun isEmpty()= size == 0

    override fun iterator() = elementData.iterator()

    override fun get(index: Int): Double {
        return elementData[index]
    }

    override fun indexOf(element: Double): Int {
        for (i in 0 until size) if (element == elementData[i]) return i
        return -1
    }

    override fun lastIndexOf(element: Double): Int {
        for (i in size - 1 downTo 0) if (element == elementData[i]) return i
        return -1
    }

    override fun listIterator(): ListIterator<Double> = ItrImpl(0)

    override fun listIterator(index: Int): ListIterator<Double> = ItrImpl(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<Double> {
        return this.toList().subList(fromIndex, toIndex)
    }

    fun add(element: Double){
        growBy(1)
        elementData[elementData.size-1] = element
    }

    operator fun set(index:Int, element: Double){
        elementData[index] = element
    }

    fun addFromArray(elements:DoubleArray){
        val prevSize = size
        growBy(elements.size)
        System.arraycopy(elements, 0, elementData, prevSize, elements.size)
    }

    fun addAll(elements: Collection<Double>){
        val prevSize = size
        growBy(elements.size)
        for((index,elem) in elements.withIndex()){
            elementData[prevSize+index] = elem
        }
    }

    /**
     * Implementation of [ListIterator] for abstract lists.
     */
    private open inner class ItrImpl(index: Int) : ListIterator<Double> {
        /** the index of the item that will be returned on the next call to [next]`()` */
        protected var index = 0

        init {
            if (index < 0 || index > size) {
                throw IndexOutOfBoundsException("index: $index, size: $size")
            }
            this.index = index
        }


        override fun hasNext(): Boolean = index < size

        override fun next(): Double {
            if (!hasNext()) throw NoSuchElementException()
            return get(index++)
        }



        override fun hasPrevious(): Boolean = index > 0

        override fun nextIndex(): Int = index

        override fun previous(): Double {
            if (!hasPrevious()) throw NoSuchElementException()
            return get(--index)
        }

        override fun previousIndex(): Int = index - 1


    }

}