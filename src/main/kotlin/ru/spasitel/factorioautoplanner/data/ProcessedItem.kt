package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.auto.RecipesDTOItem
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import ru.spasitel.factorioautoplanner.planner.TechnologyTreePlanner

data class ProcessedItem(
    val item: String,
    var amount: Double,
    val recipe: RecipesDTOItem,
    val usedIn: MutableMap<String, Double>,
    val ingredients: MutableMap<String, Double>,
    val buildingType: BuildingType,
    private val totalProductivity: Double
) {
    constructor(
        item: String,
        amount: Double,
        recipe: RecipesDTOItem
    ) : this(
        item, amount, recipe, HashMap(),
        recipe.ingredients.associate { it.name to -1.0 }.toMutableMap(),
        buildingType(recipe),
        calculateTotalProductivity(recipe, item)
    )

    fun productivity() = totalProductivity * amount

    companion object {
        private fun calculateTotalProductivity(recipe: RecipesDTOItem, item: String): Double {
            val buildingType = buildingType(recipe)
            var result = 1.0
            when (buildingType) {
                BuildingType.ASSEMBLER -> {
                    result /= 1.25
                }

                BuildingType.SMELTER -> {
                    result /= 2
                }

                else -> {}
            }
            if (item in TechnologyTreePlanner.productivity_module_limitation) {
                when (buildingType) {
                    BuildingType.ASSEMBLER, BuildingType.ROCKET_SILO -> {
                        result /= 1.4
                    }

                    BuildingType.SMELTER -> {
                        result /= 1.2
                    }

                    BuildingType.CHEMICAL_PLANT -> {
                        result /= 1.3
                    }

                    else -> {}
                }
            }
            result /= recipe.result_count?.toDouble() ?: 1.0
            result *= recipe.energy_required
            return result
        }

        private fun buildingType(recipe: RecipesDTOItem) =
            if (recipe.category == "chemistry") BuildingType.CHEMICAL_PLANT else
                if (recipe.name == "stone-brick") BuildingType.SMELTER else
                    if (recipe.name == "rocket-part") BuildingType.ROCKET_SILO else
                        BuildingType.ASSEMBLER
    }
}
