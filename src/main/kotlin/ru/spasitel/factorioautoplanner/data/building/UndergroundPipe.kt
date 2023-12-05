package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class UndergroundPipe(override val place: Place, val liquid: String, val direction: Int) : Building(place) {
    override val type: BuildingType
        get() = BuildingType.UNDERGROUND_PIPE

    override val symbol: Char
        get() = when (direction) {
            0 -> 'A'
            2 -> '}'
            4 -> 'V'
            6 -> '{'
            else -> throw RuntimeException("Unknown direction $direction")
        }

    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            JSON,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0,
            direction
        )
    }

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"pipe-to-ground\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d},"
    }
}
