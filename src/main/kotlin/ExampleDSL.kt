import sun.misc.Unsafe
import java.lang.reflect.Array.newInstance
import kotlin.reflect.KClass
import kotlin.reflect.jvm.reflect

val bytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8, 0, 32, 0, 0, 0, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1)

val description = "exampleStruct" with {
    -{ marker:    String ->    8.bits  }
    +{ signature: String ->    8.bytes }
    -{ number:    Int ->       2       } > 0x10
    -{ size:      Int ->       4       }
    +{ data:      ByteArray -> 10      }
}

fun main(args: Array<String>) {
    val token = description.token
    println("Struct name: ${token.name}")
    println("Values: ")
    token.parse(bytes) forEachValue {
        val info = description.fieldInformation[it.definition]
        if (info != null) {
            val modifier = if (info.first) "public" else "private"
            val type = info.second.simpleName
            println("    $modifier $type: $it")
        }
    }
}

infix fun String.with(setup: SequenceBuilder.() -> Unit): MappableToken {
    val sequenceBuilder = SequenceBuilder(this)
    sequenceBuilder.setup()
    return sequenceBuilder.build()
}

data class MappableToken(val token: Token, val fieldInformation: Map<Def, Pair<Boolean, KClass<*>>>)

val Int.bits: Int
    get() = this / 8

val Int.bytes: Int
    get() = this

class SequenceBuilder(private val name: String) {

    val definitions = linkedMapOf<Def, Pair<Boolean, KClass<*>>>()

    inline operator fun <reified T> ((T) -> Int).unaryPlus() {
        definitions.put(createDefinition(this), Pair(true, T::class))
    }

    inline operator fun <reified T> ((T) -> Int).unaryMinus() {
        definitions.put(createDefinition(this), Pair(false, T::class))
    }

    inline fun <reified T> createDefinition(noinline function: (T) -> Int): Def {
        val name = function.reflect()?.parameters?.get(0)?.name.orEmpty()
        val size = function.invoke(createInstanceOfT())
        return Def(name, size)
    }

    infix operator fun Unit.compareTo(value: Int) : Int {
        // TODO: how to determine operator type, as e.g. a > b gets translated to a.compareTo(b) > 0
        return 0
    }

    fun build(): MappableToken {
        val iterator = definitions.iterator()
        val sequence = definitions.entries
                .drop(2)
                .map { it.key }
                .fold(Seq(name, iterator.next().key, iterator.next().key), { seq, definition -> Seq(name, seq, definition) })
        return MappableToken(sequence, definitions)
    }
}

inline fun <reified T> createInstanceOfT(): T {
    return if (isByteArray<T>())
        newInstance(Byte::class.java, 0) as T
    else
        unsafe().allocateInstance(T::class.java) as T
}

inline fun <reified T> isByteArray(): Boolean = T::class == ByteArray::class

fun unsafe(): Unsafe {
    val unsafe = Unsafe::class.java.getDeclaredField("theUnsafe")
    unsafe.isAccessible = true
    return unsafe.get(null) as Unsafe
}