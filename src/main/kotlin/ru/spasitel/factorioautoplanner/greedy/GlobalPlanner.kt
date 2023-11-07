package ru.spasitel.factorioautoplanner.greedy

import com.google.gson.Gson
import ru.spasitel.factorioautoplanner.data.ProcessedItem
import ru.spasitel.factorioautoplanner.data.auto.RecipesDTO
import ru.spasitel.factorioautoplanner.data.auto.RecipesDTOItem
import ru.spasitel.factorioautoplanner.data.building.BuildingType

class GlobalPlanner {

    fun readRecipesFromFile(): RecipesDTO {
        val gson = Gson()
        val json = this::class.java.getResource("/recipes.json").readText()
        val fromJson = gson.fromJson(json, RecipesDTO::class.java)
        val corrected = fromJson.filter {
            it.name != "solid-fuel-from-petroleum-gas" && it.name != "solid-fuel-from-heavy-oil"
        }.map {
            if (it.results?.size == 1) {
                it.copy(result = it.results[0].name, result_count = it.results[0].amount)
            } else {
                it
            }
        }
        return RecipesDTO().apply { addAll(corrected) }
    }

    fun createRecipeTree(
        base: Set<String>, toBuild: Map<String, Double>, recipes: RecipesDTO
    ): Map<String, ProcessedItem> {
        val result = HashMap<String, ProcessedItem>()
        toBuild.forEach { (item, amount) ->
            result[item] = ProcessedItem(item, amount, findRecipe(recipes, item))
        }
        makeDependenciesTree(result, base, recipes)

        calculateAmounts(result, base, toBuild)

        return result
    }

    private fun calculateAmounts(
        result: MutableMap<String, ProcessedItem>,
        base: Set<String>,
        toBuild: Map<String, Double>
    ) {
        val done = HashSet<String>()
        val pretends = toBuild.keys.toMutableSet()

        while (pretends.isNotEmpty()) {
            val item = pretends.first {
                done.containsAll(result[it]!!.usedIn.keys)
            }
            calculateItemAmount(result, item)
            if (item !in base) {
                result[item]!!.ingredients.keys.forEach { pretends.add(it) }
            }
            pretends.remove(item)
            done.add(item)
        }

    }

    private fun calculateItemAmount(result: MutableMap<String, ProcessedItem>, item: String) {
        val processedItem = result[item]!!
        if (processedItem.amount < 0) {
            val usedIn = processedItem.usedIn
            var sum = 0.0
            usedIn.keys.forEach {
                val used = result[it]!!.ingredients[item]!!
                sum += used
                usedIn[it] = used
            }
            processedItem.amount = sum
        }
        var amount = processedItem.amount
        if (processedItem.recipe.name in productivity_module_limitation) {
            amount /= when (processedItem.buildingType) {
                BuildingType.CHEMICAL_PLANT -> 1.3
                BuildingType.SMELTER -> 1.2
                else -> 1.4
            }
        }
        val resultAmount = processedItem.recipe.result_count?.toDouble() ?: 1.0
        amount /= resultAmount
        processedItem.recipe.ingredients.forEach {
            processedItem.ingredients[it.name] = it.amount.toDouble() * amount
        }

    }

    private fun makeDependenciesTree(
        result: MutableMap<String, ProcessedItem>, base: Set<String>, recipes: RecipesDTO
    ) {
        val todo = result.keys.toMutableSet()
        while (todo.isNotEmpty()) {
            val item = todo.first()
            val processedItem = result[item]!!
            val ingredients = processedItem.ingredients
            ingredients.forEach { (ingredient, _) ->
                if (ingredient !in base) {
                    todo.add(ingredient)
                    if (ingredient !in result) {
                        val recipe = findRecipe(recipes, ingredient)
                        result[ingredient] = ProcessedItem(ingredient, -1.0, recipe)
                    }
                } else if (ingredient !in result) {

                    result[ingredient] =
                        ProcessedItem(ingredient, -1.0, EMPTY_RECIPE, HashMap(), HashMap(), BuildingType.ASSEMBLER)
                }
                result[ingredient]!!.usedIn[item] = -1.0
            }
            todo.remove(item)
        }
    }

    private fun findRecipe(
        recipes: RecipesDTO, ingredient: String
    ): RecipesDTOItem {
        if (recipes.filter { it.result == ingredient }.size > 1) {
            println("Multi recipe for $ingredient")
        }
        return recipes.first { it.result == ingredient }
    }

    companion object {
        val EMPTY_RECIPE = RecipesDTOItem(0.0, emptyList(), null, "empty", null, null, null)

        @JvmStatic
        fun main(args: Array<String>) {
            val recipes = GlobalPlanner().readRecipesFromFile()
            val base = setOf(
                "iron-plate",
                "copper-plate",
                "steel-plate",
                "coal",
                "stone",
                "water",
                "crude-oil",
                "heavy-oil",
                "light-oil",
                "petroleum-gas"
            )
            val toBuildMilitary = mapOf(
                "automation-science-pack" to 100.0,
                "logistic-science-pack" to 100.0,
                "military-science-pack" to 100.0,
                "chemical-science-pack" to 100.0,
//                "production-science-pack" to 100.0,
                "utility-science-pack" to 100.0,
                "space-science-pack" to 100.0
            )
            val treeMilitary = GlobalPlanner().createRecipeTree(base, toBuildMilitary, recipes)
            val toBuildProduction = mapOf(
                "automation-science-pack" to 100.0,
                "logistic-science-pack" to 100.0,
//                "military-science-pack" to 100.0,
                "chemical-science-pack" to 100.0,
                "production-science-pack" to 100.0,
                "utility-science-pack" to 100.0,
                "space-science-pack" to 100.0
            )
            val treeProduction = GlobalPlanner().createRecipeTree(base, toBuildProduction, recipes)
            val result = HashMap<String, ProcessedItem>()
            treeProduction.keys.plus(treeMilitary.keys).forEach {
                if (it !in treeProduction) {
                    result[it] = treeMilitary[it]!!
                } else if (it !in treeMilitary) {
                    result[it] = treeProduction[it]!!
                } else {
                    if (treeProduction[it]!!.amount > treeMilitary[it]!!.amount) {
                        result[it] = treeProduction[it]!!
                    } else {
                        result[it] = treeMilitary[it]!!
                    }
                }
            }

        }

        val productivity_module_possible_replace = setOf(
            "sulfuric-acid",
            "basic-oil-processing",
            "advanced-oil-processing",
            "coal-liquefaction",
            "heavy-oil-cracking",
            "light-oil-cracking",
            "solid-fuel-from-light-oil",
            "solid-fuel-from-heavy-oil",
            "solid-fuel-from-petroleum-gas",
            "lubricant",
            "sulfur",
            "plastic-bar",
            "rocket-fuel",
            "stone-brick",
        )

        val productivity_module_limitation = setOf(
            "iron-plate",
            "copper-plate",
            "steel-plate",
            "empty-barrel",
            "uranium-processing",
            "copper-cable",
            "iron-stick",
            "iron-gear-wheel",
            "electronic-circuit",
            "advanced-circuit",
            "processing-unit",
            "engine-unit",
            "electric-engine-unit",
            "uranium-fuel-cell",
            "explosives",
            "battery",
            "flying-robot-frame",
            "low-density-structure",
            "nuclear-fuel",
            "nuclear-fuel-reprocessing",
            "rocket-control-unit",
            "rocket-part",
            "space-science-pack",
            "automation-science-pack",
            "logistic-science-pack",
            "chemical-science-pack",
            "military-science-pack",
            "production-science-pack",
            "utility-science-pack",
            "kovarex-enrichment-process"
        )

    }
}