import Test.plus

class Value

object Test {
    infix operator fun Value.plus(other: Value) = Value()
}

fun main(args: Array<String>) {
    val test = Value() + Value()
}