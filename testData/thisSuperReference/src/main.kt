class A {
    open fun f() {}
}

class B(x: Int) : A() {
    constructor() : this(0)

    constructor(x: Int, y: Int) : this(0)

    override fun f() = super.f()

    fun g() = this.f()

    fun h() = this.f()
}

fun main(args: Array<String>) {
    B().g()
}
