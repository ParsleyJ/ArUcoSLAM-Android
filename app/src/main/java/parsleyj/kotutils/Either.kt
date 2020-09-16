package parsleyj.kotutils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

sealed class Either<T1, T2> {
    class Left<T1, T2>(val get:T1): Either<T1, T2>()
    class Right<T1, T2>(val get:T2): Either<T1, T2>()

    fun wrap(x:T1) = Left<T1, T2>(x)
    fun wrap(x:T2) = Right<T1, T2>(x)

    fun onLeft(block:(T1)->Unit): Either<T1, T2> {
        if(this is Left<T1, T2>){
            block(this.get)
        }
        return this
    }
    fun onRight(block:(T2)->Unit): Either<T1, T2> {
        if(this is Right<T1, T2>){
            block(this.get)
        }
        return this
    }
}

inline fun <T1, T2, R1> Either<T1, T2>.mapLeft(block: (T1) -> R1): Either<R1, T2> = when(this){
    is Either.Left<T1, T2> -> Either.Left(block(this.get))
    is Either.Right<T1, T2> -> Either.Right(this.get)
}

inline fun <T1, T2, R2> Either<T1, T2>.mapRight(block: (T2) -> R2): Either<T1, R2> = when(this){
    is Either.Left<T1, T2> -> Either.Left(this.get)
    is Either.Right<T1, T2> -> Either.Right(block(this.get))
}

inline fun <T, R> Either<T, T>.mapBoth(block:(T)->R): Either<R, R> = when(this){
    is Either.Left<T, T> -> Either.Left(block(this.get))
    is Either.Right<T, T> -> Either.Right(block(this.get))
}

typealias MJust<T> = Either.Left<T, Unit>
typealias MNothing<T> = Either.Right<T, Unit>
typealias Maybe<T> = Either<T, Unit>

@OptIn(ExperimentalContracts::class)
fun <T> Maybe<T>.isSomething():Boolean{
    contract {
        returns(true) implies (this@isSomething is MJust<T>)
        returns(false) implies (this@isSomething is MNothing<T>)
    }
    return this is MJust<T>
}

@OptIn(ExperimentalContracts::class)
fun <T> Maybe<T>.isNothing():Boolean{
    contract {
        returns(true) implies (this@isNothing is MNothing<T>)
        returns(false) implies (this@isNothing is MJust<T>)
    }
    return this is MNothing<T>
}

fun <T> nothing() = MNothing<T>(Unit)
fun <T> just(v:T) = MJust(v)

fun <T,R> Maybe<T>.map(block:(T)->R): Maybe<R> {
    return this.mapLeft(block)
}