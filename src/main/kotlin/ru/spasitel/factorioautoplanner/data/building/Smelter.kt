package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*


data class Smelter(override val place: Place) : Building(place) {
    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            SMELTER,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0
        )
    }

    override val type: BuildingType
        get() = BuildingType.SMELTER
    override val symbol: Char
        get() = 's'

    companion object {
        private const val SMELTER =
            "{\"entity_number\":%d,\"name\":\"electric-furnace\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"speed-module-3\":2}},"
    }

    override fun toString(): String {
        return super.toString()
    }
}