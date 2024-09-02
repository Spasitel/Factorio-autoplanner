package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.planner.TechnologyTreePlanner
import java.util.*


data class Smelter(override val place: Place, var recipe: String, var moduleLvl: Int = 3) : Building(place) {
    override fun toJson(number: Int): String {
        val fixedRecipe = recipe.split("#")[0]
        val item = when (fixedRecipe) {
            in TechnologyTreePlanner.productivity_module_limitation -> "productivity-module-3"
            in TechnologyTreePlanner.productivity_module_limitation_lvl1 -> "productivity-module"
            else -> Utils.speedModule(moduleLvl)
        }

        return String.format(
            Locale.US,
            SMELTER,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0,
            item
        )
    }

    override val type: BuildingType
        get() = BuildingType.SMELTER
    override val symbol: Char
        get() = 'S'

    companion object {
        private const val SMELTER =
            "{\"entity_number\":%d,\"name\":\"electric-furnace\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"%s\":2}},"
    }

    override fun toString(): String {
        return super.toString()
    }
}