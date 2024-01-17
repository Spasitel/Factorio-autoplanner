package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class Roboport(override val place: Place) : Building(place) {
    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            JSON,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0
        )
    }

    override val type: BuildingType
        get() = BuildingType.ROBOPORT
    override val symbol: Char
        get() = 'P'

    companion object {
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"roboport\",\"position\":{\"x\":%.1f,\"y\":%.1f}},"
    }

    override fun toString(): String {
        return super.toString()
    }
}
