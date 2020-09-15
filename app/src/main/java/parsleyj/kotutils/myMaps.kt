package parsleyj.kotutils

/**
 * Just an alias for [to] which is more expressive for "result pair".
 */
@DslMarker
annotation class WithDSL

@WithDSL
infix fun<T1, T2> T1.with(b:T2):Pair<T1, T2>{
    return this to b
}