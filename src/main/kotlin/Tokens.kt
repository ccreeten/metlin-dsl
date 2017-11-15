typealias ParseResult = Triple<Boolean, Int, List<Value>>

infix fun ParseResult.forEachValue(consumer: (Value) -> Unit) = this.third.forEach(consumer)

data class Value(val definition: Def, val offset: Int) {
    override fun toString(): String = "Value(name=${definition.name}, size=${definition.size})"
}

abstract class Token(val name: String) {
    abstract fun parse(input: ByteArray, offset: Int = 0, result: List<Value> = emptyList()): ParseResult
}

class Def(name: String, val size: Int) : Token(name) {
    override fun parse(input: ByteArray, offset: Int, result: List<Value>): ParseResult =
            if (offset + size <= input.size)
                Triple(true, offset + size, result + Value(this, offset))
            else
                Triple(false, offset, result)
}

class Seq(name: String, val left: Token, val right: Token) : Token(name) {
    constructor(left: Token, right: Token) : this("", left, right)

    override fun parse(input: ByteArray, offset: Int, result: List<Value>): ParseResult =
            left.parse(input, offset, result).let {
                if (it.first)
                    right.parse(input, it.second, it.third)
                else
                    Triple(false, offset, result)
            }
}

class Cho(name: String, val left: Token, val right: Token) : Token(name) {
    constructor(left: Token, right: Token) : this("", left, right)

    override fun parse(input: ByteArray, offset: Int, result: List<Value>): Triple<Boolean, Int, List<Value>> =
            left.parse(input, offset, result).let {
                if (it.first)
                    Triple(true, it.second, it.third)
                else
                    right.parse(input, offset, result)
            }
}

class Rep(name: String, val operand: Token) : Token(name) {
    constructor(operand: Token) : this("", operand)

    tailrec override fun parse(input: ByteArray, offset: Int, result: List<Value>): ParseResult {
        val (success, outOffset, outResult) = operand.parse(input, offset, result)
        return if (!success)
            Triple(true, outOffset, outResult)
        else
            parse(input, outOffset, outResult)
    }
}

