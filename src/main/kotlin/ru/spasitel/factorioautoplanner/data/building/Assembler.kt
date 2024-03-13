package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import ru.spasitel.factorioautoplanner.planner.TechnologyTreePlanner
import java.util.*

data class Assembler(override val place: Place, val direction: Int?, val recipe: String, var moduleLvl: Int = 3) :
    Building(place) {

    //+4 -5
    //+6 -10
    //+10 -15

    fun speed(): Double {
        val fixedRecipe = recipe.split("#")[0]
        if (fixedRecipe in TechnologyTreePlanner.productivity_module_limitation || fixedRecipe in TechnologyTreePlanner.productivity_module_limitation_lvl1) {
            return when (moduleLvl) {
                1 -> 0.8
                2 -> 0.6
                3 -> 0.4
                else -> 1.0
            }
        }
        return when (moduleLvl) {
            1 -> 1.8
            2 -> 2.2
            3 -> 3.0
            else -> 1.0
        }
    }

    override fun toJson(number: Int): String {
        val fixedRecipe = recipe.split("#")[0]
        val item = when (fixedRecipe) {
            in TechnologyTreePlanner.productivity_module_limitation -> "productivity-module-3"
            in TechnologyTreePlanner.productivity_module_limitation_lvl1 -> "productivity-module"
            else -> "speed-module-$moduleLvl" //todo lvl 1
        }
        return if (direction == null)
            String.format(
                Locale.US,
                JSON,
                number,
                fixedRecipe,
                place.start.x + type.size / 2.0,
                place.start.y + +type.size / 2.0,
                item
            )
        else
            String.format(
                Locale.US,
                JSON_DIR,
                number,
                fixedRecipe,
                place.start.x + type.size / 2.0,
                place.start.y + +type.size / 2.0,
                direction,
                item
            )
    }

    override val type: BuildingType
        get() = BuildingType.ASSEMBLER
    override val symbol: Char
        get() = 'A'

    companion object {
        private const val JSON_DIR =
            "{\"entity_number\":%d,\"name\":\"assembling-machine-3\",\"recipe\":\"%s\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d,\"items\":{\"%s\":4}},"
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"assembling-machine-3\",\"recipe\":\"%s\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"%s\":4}},"
    }

    override fun toString(): String {
        return super.toString() + " $recipe"
    }
}