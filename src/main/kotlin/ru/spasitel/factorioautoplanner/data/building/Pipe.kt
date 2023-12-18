package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Direction
import ru.spasitel.factorioautoplanner.data.LiquidConnection
import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class Pipe(override val place: Place, override var liquid: String) : LiquidBuilding, Building(place) {
    override val type: BuildingType
        get() = BuildingType.PIPE

    override val symbol: Char
        get() = '+'

    override fun toJson(number: Int): String {
        return String.format(Locale.US, PIPE, number, place.start.x + type.size / 2.0, place.start.y + type.size / 2.0)
    }

    override fun getLiquidConnections(): List<LiquidConnection> {
        return listOf(
            LiquidConnection(
                place.start,
                place.start.up(),
                liquid,
                Direction.UP
            ),
            LiquidConnection(
                place.start,
                place.start.down(),
                liquid,
                Direction.DOWN
            ),
            LiquidConnection(
                place.start,
                place.start.left(),
                liquid,
                Direction.LEFT
            ),
            LiquidConnection(
                place.start,
                place.start.right(),
                liquid,
                Direction.RIGHT
            )
        )
    }

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        private const val PIPE =
            "{\"entity_number\":%d,\"name\":\"pipe\",\"position\":{\"x\":%.1f,\"y\":%.1f}},"
    }
}