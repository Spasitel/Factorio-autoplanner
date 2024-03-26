package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import ru.spasitel.factorioautoplanner.data.Utils
import java.util.*


data class Beacon(override val place: Place, val moduleLvl: Int = 3) : Building(place) {
    override val type: BuildingType
        get() = BuildingType.BEACON

    override val symbol: Char
        get() = 'B'

    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            BEACON,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0,
            Utils.speedModule(moduleLvl)
        )
    }

    fun speed(): Double {
        return when (moduleLvl) {
            1 -> 0.2
            2 -> 0.3
            3 -> 0.5
            else -> throw RuntimeException("Unknown module level")
        }
    }

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        private const val BEACON =
            "{\"entity_number\":%d,\"name\":\"beacon\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"%s\":2}},"
    }
}