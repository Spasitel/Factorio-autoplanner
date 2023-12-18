package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.Direction
import ru.spasitel.factorioautoplanner.data.LiquidConnection
import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class StorageTank(override val place: Place, override var liquid: String, val direction: Int) : LiquidBuilding,
    Building(place) {
    override val type: BuildingType
        get() = BuildingType.STORAGE_TANK

    override val symbol: Char
        get() = when (direction) {
            0 -> '\\'
            2 -> '/'
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

    override fun getLiquidConnections(): List<LiquidConnection> {
        return when (direction) {
            0 -> listOf(
                LiquidConnection(place.start, place.start.up(), liquid, Direction.UP),
                LiquidConnection(place.start, place.start.left(), liquid, Direction.LEFT),
                LiquidConnection(
                    place.start.right().right().down().down(),
                    place.start.right().right().down().down().right(),
                    liquid,
                    Direction.RIGHT
                ),
                LiquidConnection(
                    place.start.right().right().down().down(),
                    place.start.right().right().down().down().down(),
                    liquid,
                    Direction.DOWN
                )
            )

            2 -> listOf(
                LiquidConnection(place.start.right().right(), place.start.right().right().up(), liquid, Direction.UP),
                LiquidConnection(
                    place.start.right().right(),
                    place.start.right().right().right(),
                    liquid,
                    Direction.RIGHT
                ),
                LiquidConnection(place.start.down().down(), place.start.down().down().left(), liquid, Direction.LEFT),
                LiquidConnection(place.start.down().down(), place.start.down().down().down(), liquid, Direction.DOWN)
            )

            else -> throw RuntimeException("Unknown direction $direction")
        }
    }

    override fun toString(): String {
        return super.toString()
    }

    fun connections(): Set<Cell> {
        return when (direction) {
            0 -> setOf(place.start, place.start.down().down().right().right())
            2 -> setOf(place.start.down().down(), place.start.right().right())
            else -> throw RuntimeException("Unknown direction $direction")
        }
    }

    companion object {
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"storage-tank\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d},"
    }
}