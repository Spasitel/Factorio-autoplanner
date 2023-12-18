package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import ru.spasitel.factorioautoplanner.planner.TechnologyTreePlanner
import java.util.*

data class Assembler(override val place: Place, val direction: Int?, val recipe: String) : Building(place) {
    override fun toJson(number: Int): String {
        val item = if (recipe in TechnologyTreePlanner.productivity_module_limitation)
            "productivity-module-3"
        else "speed-module-3"

        return if (direction == null)
            String.format(
                Locale.US,
                JSON,
                number,
                recipe,
                place.start.x + type.size / 2.0,
                place.start.y + +type.size / 2.0,
                item
            )
        else
            String.format(
                Locale.US,
                JSON_DIR,
                number,
                recipe,
                place.start.x + type.size / 2.0,
                place.start.y + +type.size / 2.0,
                direction,
                item
            )
    }

    override val type: BuildingType
        get() = BuildingType.ASSEMBLER
    override val symbol: Char
        get() = 'a'

    companion object {
        private const val JSON_DIR =
            "{\"entity_number\":%d,\"name\":\"assembling-machine-3\",\"recipe\":\"%s\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d,\"items\":{\"%s\":4}},"
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"assembling-machine-3\",\"recipe\":\"%s\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"%s\":4}},"
    }

    override fun toString(): String {
        return super.toString()
    }
}