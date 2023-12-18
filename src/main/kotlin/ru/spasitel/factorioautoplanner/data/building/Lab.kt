package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class Lab(override val place: Place) : Building(place) {
    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            JSON,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + +type.size / 2.0,
        )
    }

    override val type: BuildingType
        get() = BuildingType.LAB
    override val symbol: Char
        get() = 'L'

    companion object {
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"lab\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"productivity-module-3\":2}},"
    }

    override fun toString(): String {
        return super.toString()
    }
}
