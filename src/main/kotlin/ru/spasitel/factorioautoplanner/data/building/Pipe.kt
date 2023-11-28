package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class Pipe(override val place: Place, val liquid: String) : Building(place) {
    override val type: BuildingType
        get() = BuildingType.PIPE

    override val symbol: Char
        get() = '+'

    override fun toJson(number: Int): String {
        return String.format(Locale.US, PIPE, number, place.start.x + type.size / 2.0, place.start.y + type.size / 2.0)
    }

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        private const val PIPE =
            "{\"entity_number\":%d,\"name\":\"pipe\",\"position\":{\"x\":%.1f,\"y\":%.1f}},"
    }
}