package ru.spasitel.factorioautoplanner.simple.building

import lombok.EqualsAndHashCode
import lombok.ToString
import java.util.*

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class Inserter(x: Int, y: Int, private val direction: Int) : Building(x, y) {
    override fun toJson(number: Int): String {
        return String.format(Locale.US, INSERTER, number, x + 0.5, y + 0.5, direction)
    }

    override val type: Type
        get() = Type.INSERTER
    override val size: Int
        get() = 1
    override val symbol: Char
        get() = when (direction) {
            2 -> '>'
            4 -> 'v'
            6 -> '<'
            else -> '^'
        }

    companion object {
        const val INSERTER =
            "{\"entity_number\":%d,\"name\":\"fast-inserter\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d},"
    }
}