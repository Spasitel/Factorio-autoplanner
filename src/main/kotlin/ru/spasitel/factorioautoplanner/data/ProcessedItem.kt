package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.auto.RecipesDTOItem
import ru.spasitel.factorioautoplanner.data.building.BuildingType

data class ProcessedItem(
    val item: String,
    var amount: Double,
    val recipe: RecipesDTOItem,
    val usedIn: MutableMap<String, Double>,
    val ingredients: MutableMap<String, Double>,
    val buildingType: BuildingType
) {
    constructor(
        item: String,
        amount: Double,
        recipe: RecipesDTOItem
    ) : this(
        item, amount, recipe, HashMap(),
        recipe.ingredients.associate { it.name to -1.0 }.toMutableMap(),
        if (recipe.category == "chemistry") BuildingType.CHEMICAL_PLANT else
            if (recipe.name == "stone-brick") BuildingType.SMELTER else
                if (recipe.name == "rocket-part") BuildingType.ROCKET_SILO else
                    BuildingType.ASSEMBLER
    )
}
