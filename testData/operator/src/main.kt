import Test.plus
import Test.minus

class Value

object Test {
    infix operator fun Value.plus(other: Value) = Value()
    infix operator fun Value.minus(other: Value) = Value()
}

fun main(args: Array<String>) {
    val test = Value() + Value()
}
