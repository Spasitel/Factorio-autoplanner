package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.*
import java.util.*

data class OilRefinery(override val place: Place, val direction: Int, var moduleLvl: Int = 3) :
    LiquidConnectionsBuilding, Building(place) {
    fun speed(): Double {
        return when (moduleLvl) {
            1 -> 1.6
            2 -> 1.9
            3 -> 2.5
            else -> 1.0
        }
    }
    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            OIL_REFINERY,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + +type.size / 2.0,
            direction,
            Utils.speedModule(moduleLvl)
        )
    }

    override val type: BuildingType
        get() = BuildingType.OIL_REFINERY
    override val symbol: Char
        get() = 'O'

    companion object {
        private const val OIL_REFINERY =
            "{\"entity_number\":%d,\"name\":\"oil-refinery\",\"recipe\":\"advanced-oil-processing\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d,\"items\":{\"%s\":3}},"
    }

    fun getInputs(): Map<String, Cell> {
        when (direction) {
            0 -> return mapOf(
                "crude-oil" to Cell(place.start.x + 3, place.start.y + 4),
                "water" to Cell(place.start.x + 1, place.start.y + 4)
            )

            2 -> return mapOf(
                "crude-oil" to Cell(place.start.x, place.start.y + 3),
                "water" to Cell(place.start.x, place.start.y + 1)
            )

            4 -> return mapOf(
                "crude-oil" to Cell(place.start.x + 1, place.start.y),
                "water" to Cell(place.start.x + 3, place.start.y)
            )

            6 -> return mapOf(
                "crude-oil" to Cell(place.start.x + 4, place.start.y + 1),
                "water" to Cell(place.start.x + 4, place.start.y + 3)
            )

            else -> throw RuntimeException("Unknown direction")
        }
    }

    private fun getOutputs(): Map<String, Cell> {
        val start = when (direction) {
            0 -> place.start
            2 -> place.start.move(Direction.RIGHT, 4)
            4 -> place.start.move(Direction.DOWN, 4).move(Direction.RIGHT, 4)
            6 -> place.start.move(Direction.DOWN, 4)

            else -> throw RuntimeException("Unknown direction")
        }
        val d = Direction.fromInt(this.direction).turnRight()

        return mapOf(
//            "heavy-oil" to start,
//            "light-oil" to start.move(d).move(d),
//            "petroleum-gas" to start.move(d, 4)
            "empty" to start,
            "empty" to start.move(d).move(d),
            "empty" to start.move(d, 4)
        )
    }

    override fun getLiquidConnections(): List<LiquidConnection> {
        return getOutputs().map { (liquid, cell) ->
            LiquidConnection(
                cell,
                cell.move(Direction.fromInt(direction)),
                liquid,
                Direction.fromInt(direction)
            )
        }.toList()
    }

    override fun toString(): String {
        return super.toString()
    }
}