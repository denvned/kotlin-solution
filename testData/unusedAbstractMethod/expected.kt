class A {
}

class B : A() {
    fun f() {}
}

fun main(args: Array<String>) {
    B().f()
}