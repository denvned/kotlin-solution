fun main(args: Array<String>) {
    usedA()
}

fun usedA() {
    usedB()
}

fun usedB() {
}

fun unusedA() {
    unusedB()
}

fun unusedB() {
}
