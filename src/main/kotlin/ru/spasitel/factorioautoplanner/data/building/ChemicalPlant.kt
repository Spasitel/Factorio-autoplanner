package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Direction
import ru.spasitel.factorioautoplanner.data.LiquidConnection
import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class ChemicalPlant(override val place: Place, val direction: Int, val recipe: String) : LiquidConnectionsBuilding,
    Building(place) {
    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            JSON,
            number,
            recipe,
            place.start.x + type.size / 2.0,
            place.start.y + +type.size / 2.0,
            direction
        )
    }

    override fun getLiquidConnections(): List<LiquidConnection> {
        val start = when (direction) {
            4 -> place.start.up()
            6 -> place.start.move(Direction.RIGHT, 3)
            0 -> place.start.move(Direction.DOWN, 3).move(Direction.RIGHT, 2)
            2 -> place.start.move(Direction.DOWN, 2).left()

            else -> throw RuntimeException("Unknown direction")
        }
        val d = Direction.fromInt(this.direction).turnLeft()

        return listOf(
            LiquidConnection(start.move(d.turnRight()), start, recipe, d.turnLeft()),
            LiquidConnection(start.move(d).move(d).move(d.turnRight()), start.move(d).move(d), recipe, d.turnLeft())
        )
    }

    override val type: BuildingType
        get() = BuildingType.CHEMICAL_PLANT
    override val symbol: Char
        get() = 'P'

    companion object {
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"chemical-plant\",\"recipe\":\"%s\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d,\"items\":{\"speed-module-3\":3}},"
    }

    override fun toString(): String {
        return super.toString()
    }
}