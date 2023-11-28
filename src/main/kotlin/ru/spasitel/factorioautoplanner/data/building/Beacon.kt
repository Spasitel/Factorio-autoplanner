package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*


data class Beacon(override val place: Place) : Building(place) {
    override val type: BuildingType
        get() = BuildingType.BEACON

    override val symbol: Char
        get() = 'b'

    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            BEACON,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0
        )
    }

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        private const val BEACON =
            "{\"entity_number\":%d,\"name\":\"beacon\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"speed-module-3\":2}},"
    }
}