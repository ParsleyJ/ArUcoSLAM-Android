package parsleyj.arucoslam.pipeline

import parsleyj.kotutils.MutableOptional
import parsleyj.kotutils.generateIt
import parsleyj.kotutils.with


interface Pipeline<in T, out R> {
    fun supply(input: T?)
    fun retrieve(): R

    companion object {
        fun tokenGen() = generateIt<Long> {
            var x = 0L
            while (true) {
                yield(x++)
            }
        }
    }
}


fun <T, R> pipeline(
    supplyEmptyOutput: () -> R,
    maxProcessors: Int = 4,
    block: suspend (T, R) -> Unit,
): Pipeline<T, R> = ProcessorPool(
    Unit,
    maxProcessors,
    supplyEmptyOutput,
    { Unit },
    block = { _: Unit, inp: T, out: R, _: Unit -> block(inp, out) }
)

fun <T, R> pipeline(
    maxProcessors: Int = 4,
    block: (T) -> R,
): Pipeline<T, MutableOptional<R>> = pipeline({ MutableOptional.empty() }, maxProcessors, { t, r ->
    r.mutAssign { block(t) }
})

fun <T, X, R> Pipeline<T, X>.then(other: Pipeline<X, R>): Pipeline<T, R> {
    return object : Pipeline<T, R> {
        override fun supply(input: T?) {
            this@then.supply(input)
            other.supply(this@then.retrieve())
        }

        override fun retrieve(): R {
            return other.retrieve()
        }
    }
}

fun <T, R1, R2> Pipeline<T, R1>.diamond(other: Pipeline<T, R2>): Pipeline<T, Pair<R1, R2>>{
    return object : Pipeline<T, Pair<R1, R2>>{
        override fun supply(input: T?) {
            this@diamond.supply(input)
            other.supply(input)
        }

        override fun retrieve(): Pair<R1, R2> {
            return this@diamond.retrieve() with other.retrieve()
        }

    }
}


