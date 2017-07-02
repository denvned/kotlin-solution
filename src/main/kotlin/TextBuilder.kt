private val TRAILING_WHITESPACE = "[^\\n]+\\n".toRegex()
private val MULTIPLE_EMPTY_LINES = "\\n{3,}".toRegex()

internal class TextBuilder {
    private val builder = StringBuilder()
    private val whitespaceBuilder = StringBuilder()

    fun append(text: String) {
        if (text.isNotEmpty()) {
            if (builder.isEmpty()) {
                builder.append(whitespaceBuilder.takeLastWhile { it != '\n' })
            } else {
                builder.append(whitespaceBuilder
                        .replace(TRAILING_WHITESPACE, "\n")
                        .replace(MULTIPLE_EMPTY_LINES, "\n\n"))
            }
            whitespaceBuilder.setLength(0)
            builder.append(text)
        }
    }

    fun appendWhitespace(space: String) {
        if (space.isNotEmpty()) {
            val i = whitespaceBuilder.lastIndexOf('\n')
            if (i == -1) {
                whitespaceBuilder.setLength(0)
                whitespaceBuilder.append(space)
            } else if ('\n' in space) {
                whitespaceBuilder.setLength(i)
                whitespaceBuilder.append(space)
            }
        }
    }

    override fun toString(): String {
        return builder.toString()
    }
}
