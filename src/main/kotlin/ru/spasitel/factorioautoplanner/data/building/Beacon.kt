package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*


data class Beacon(override val place: Place) : Building(place) {
    override val type: BuildingType
        get() = BuildingType.BEACON

    override val symbol: Char
        get() = 'b'

    override fun toJson(number: Int): String {
        return String.format(Locale.US, BEACON, number, place.start.x + 1.5, place.start.y + 1.5)
    }

    companion object {
        private const val BEACON =
            "{\"entity_number\":%d,\"name\":\"beacon\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"speed-module-3\":2}},"
    }
}