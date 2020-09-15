@file:Suppress("ClassName")
package parsleyj.kotutils

@DslMarker
annotation class SquareLiterals

@SquareLiterals
object list

@SquareLiterals
object mutList

@SquareLiterals
object doubleArray

@SquareLiterals
object intArray


operator fun <T> list.get(vararg values: T): List<T> {
    return listOf(*values)
}

@SquareLiterals
operator fun <T> list.invoke() = listOf<T>()

@SquareLiterals
fun <T> list.empty() = list<T>()

operator fun <T> mutList.get(vararg values: T): MutableList<T> {
    return mutableListOf(*values)
}

@SquareLiterals
operator fun <T> mutList.invoke() = mutableListOf<T>()

@SquareLiterals
fun <T> mutList.empty() = mutableListOf<T>()

operator fun doubleArray.get(vararg values: Double): DoubleArray {
    return doubleArrayOf(*values)
}

operator fun intArray.get(vararg values: Int): IntArray {
    return intArrayOf(*values)
}

