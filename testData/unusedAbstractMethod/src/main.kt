class A {
    abstract fun f()
}

class B : A() {
    override fun f() {}
}

fun main(args: Array<String>) {
    B().f()
}
