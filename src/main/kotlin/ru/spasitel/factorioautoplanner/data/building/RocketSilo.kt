package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class RocketSilo(override val place: Place, val direction: Int) : Building(place) {
    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            JSON,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + +type.size / 2.0,
            direction
        )
    }

    override val type: BuildingType
        get() = BuildingType.ROCKET_SILO
    override val symbol: Char
        get() = 'R'

    companion object {
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"rocket-silo\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d,\"items\":{\"productivity-module-3\":4}},"
    }

    override fun toString(): String {
        return super.toString()
    }
}

