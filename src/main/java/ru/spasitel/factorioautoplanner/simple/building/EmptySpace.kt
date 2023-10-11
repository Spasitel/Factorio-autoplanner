package ru.spasitel.factorioautoplanner.simple.building

data class EmptySpace(override val x: Int, override val y: Int, override val size: Int) : Building(x, y) {
    override fun toJson(number: Int): String {
        throw RuntimeException()
    }

    override val type: Type
        get() = Type.EMPTY
    override val symbol: Char
        get() = 'x'
}