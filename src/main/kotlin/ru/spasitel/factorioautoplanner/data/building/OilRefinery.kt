package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class OilRefinery(override val place: Place, val direction: Int) : Building(place) {
    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            OIL_REFINERY,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + +type.size / 2.0,
            direction
        )
    }

    override val type: BuildingType
        get() = BuildingType.OIL_REFINERY
    override val symbol: Char
        get() = 'o'

    companion object {
        private const val OIL_REFINERY =
            "{\"entity_number\":%d,\"name\":\"oil-refinery\",\"recipe\":\"advanced-oil-processing\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d,\"items\":{\"speed-module-3\":3}},"
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

    override fun toString(): String {
        return super.toString()
    }
}