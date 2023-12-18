package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class Pump(override val place: Place, val direction: Int) : Building(place) {
    override val type: BuildingType
        get() = BuildingType.PUMP

    override val symbol: Char
        get() = when (direction) {
            0 -> 'A'
            2 -> '}'
            4 -> 'V'
            6 -> '{'
            else -> throw RuntimeException("Unknown direction $direction")
        }

    fun alongX(): Boolean {
        return direction == 2 || direction == 6
    }

    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            JSON,
            number,
            place.start.x + if (alongX()) type.size / 2.0 else 0.5,
            place.start.y + if (!alongX()) type.size / 2.0 else 0.5,
            direction
        )
    }

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"pump\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d},"
    }
}

