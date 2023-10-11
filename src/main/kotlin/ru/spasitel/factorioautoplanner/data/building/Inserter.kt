package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*


data class Inserter(override val place: Place, private val direction: Int) : Building(place) {
    override fun toJson(number: Int): String {
        return String.format(Locale.US, INSERTER, number, place.start.x + 0.5, place.start.y + 0.5, direction)
    }

    override val type: BuildingType
        get() = BuildingType.INSERTER

    override val symbol: Char
        get() = when (direction) {
            2 -> '>'
            4 -> 'v'
            6 -> '<'
            else -> '^'
        }

    companion object {
        const val INSERTER =
            "{\"entity_number\":%d,\"name\":\"fast-inserter\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d},"
    }
}