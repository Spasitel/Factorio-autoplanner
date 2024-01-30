package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Direction
import ru.spasitel.factorioautoplanner.data.LiquidConnection
import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class UndergroundPipe(override val place: Place, override var liquid: String, val direction: Int) : LiquidBuilding,
    Building(place) {
    override val type: BuildingType
        get() = BuildingType.UNDERGROUND_PIPE

    override val symbol: Char
        get() = when (direction) {
            4 -> 'A'
            6 -> '}'
            0 -> 'V'
            2 -> '{'
            else -> throw RuntimeException("Unknown direction $direction")
        }

    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            JSON,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0,
            direction
        )
    }

    fun alongX(): Boolean {
        return direction == 0 || direction == 4
    }

    override fun getLiquidConnections(): List<LiquidConnection> {
        return listOf(LiquidConnection(place.start, place.start.move(direction), liquid, Direction.fromInt(direction)))
    }

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"pipe-to-ground\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d},"
    }
}
