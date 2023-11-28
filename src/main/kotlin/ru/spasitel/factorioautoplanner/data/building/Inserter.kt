package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*


data class Inserter(override val place: Place, val direction: Int) : Building(place) {
    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            INSERTER,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0,
            direction
        )
    }

    override val type: BuildingType
        get() = BuildingType.INSERTER

    override val symbol: Char
        get() = when (direction) {
            0 -> '^'
            2 -> '>'
            4 -> 'v'
            6 -> '<'
            else -> throw RuntimeException("Unknown direction $direction")
        }

    fun alongX(): Boolean {
        return direction == 2 || direction == 6
    }

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        const val INSERTER =
            "{\"entity_number\":%d,\"name\":\"fast-inserter\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d},"
    }
}