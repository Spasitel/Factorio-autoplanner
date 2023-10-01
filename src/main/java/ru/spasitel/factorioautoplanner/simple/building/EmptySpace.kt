package ru.spasitel.factorioautoplanner.simple.building

class EmptySpace(x: Int, y: Int, override val size: Int) : Building(x, y) {
    override fun toJson(number: Int): String {
        throw RuntimeException()
    }

    override val type: Type
        get() = Type.EMPTY
    override val symbol: Char
        get() = 'x'
}