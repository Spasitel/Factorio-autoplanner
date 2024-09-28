package ru.spasitel.factorioautoplanner.planner

import com.google.gson.Gson
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.ProcessedItem
import ru.spasitel.factorioautoplanner.data.auto.RecipesDTO
import ru.spasitel.factorioautoplanner.data.auto.RecipesDTOItem
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import java.util.*

class TechnologyTreePlanner {

    fun readRecipesFromFile(): RecipesDTO {
        val gson = Gson()
        val json = this::class.java.getResource("/recipes.json")!!.readText()
        val fromJson = gson.fromJson(json, RecipesDTO::class.java)
        val corrected = fromJson.filter {
            it.name !in excluded_recipes
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
        if (processedItem.recipe.name in productivity_module_limitation_lvl1) {
            amount /= 1.16 //todo: minor. module lvl
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
                        ProcessedItem(ingredient, -1.0, EMPTY_RECIPE, HashMap(), HashMap(), BuildingType.ASSEMBLER, 0.0)
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
            logger.info { "Multi recipe for $ingredient" }
        }
        return recipes.first { it.result == ingredient }
    }

    companion object {
        private val logger = KotlinLogging.logger {}

        @JvmStatic
        fun main(args: Array<String>) {
            val tree = scienceRoundTree()
            val amountMap = TreeMap<Double, Pair<String, String>>()
            tree.forEach { (key, value) ->
                value.ingredients.filter { it.key !in setOf("petroleum-gas", "lubricant", "sulfuric-acid") }
                    .forEach { (ingredient, amount) ->
                        var amount1 = amount
                        while (amountMap[amount1] != null) {

                            amount1 += 0.0001
                        }
                        amountMap[amount1] = Pair(key, ingredient)

                    }
            }
            logger.info { amountMap }

            tree.forEach { (key, value) ->
                val productivity = value.productivity()
                val prod = if (value.recipe.name in productivity_module_limitation) 0.4 else 3.0
                val buildings = productivity.div(prod)
                logger.info { "$key:\t $buildings \t$productivity \t${value.amount} " }
            }
        }


        val EMPTY_RECIPE = RecipesDTOItem(0.0, emptyList(), null, "empty", null, null, null)
        val base = if (GlobalPlanner.isSmelter)
            setOf("iron-ore", "copper-ore")
        else
            setOf(
//                "iron-plate",
//                "copper-plate",
//                "steel-plate",
                "iron-ore",
                "copper-ore",
                "coal",
                "stone",
                "water",
                "crude-oil",
                "heavy-oil",
                "light-oil",
                "petroleum-gas",
//            "sulfuric-acid",
//            "sulfur",
//            "lubricant",
//            "rocket-fuel",
            )

        fun scienceRoundTree(): Map<String, ProcessedItem> {
            val recipes = TechnologyTreePlanner().readRecipesFromFile()
            val toBuild = mapOf(
                "science-approximation" to 1.0
            )
            return TechnologyTreePlanner().createRecipeTree(base, toBuild, recipes)
        }

        fun scienceTree(): Map<String, ProcessedItem> {
            val recipes = TechnologyTreePlanner().readRecipesFromFile()
            val toBuildMilitary = mapOf(
                "automation-science-pack" to 1.0,
                "logistic-science-pack" to 1.0,
                "military-science-pack" to 1.0,
                "chemical-science-pack" to 1.0,
//                "production-science-pack" to 1.0,
                "utility-science-pack" to 1.0,
                "space-science-pack" to 1.0
            )
            val treeMilitary = TechnologyTreePlanner().createRecipeTree(base, toBuildMilitary, recipes)
            val toBuildProduction = mapOf(
                "automation-science-pack" to 1.0,
                "logistic-science-pack" to 1.0,
//                "military-science-pack" to 1.0,
                "chemical-science-pack" to 1.0,
                "production-science-pack" to 1.0,
                "utility-science-pack" to 1.0,
                "space-science-pack" to 1.0
            )
            val treeProduction = TechnologyTreePlanner().createRecipeTree(base, toBuildProduction, recipes)
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
            return result
        }

        fun smelterTree(): Map<String, ProcessedItem> {
            val recipes = TechnologyTreePlanner().readRecipesFromFile()
            val toBuild = mapOf(
                GlobalPlanner.smelterType + "-plate" to 1.0
            )
            return TechnologyTreePlanner().createRecipeTree(setOf(GlobalPlanner.smelterOre + "-ore"), toBuild, recipes)

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
            "electric-engine-unit",
            "battery",
            "automation-science-pack",
            "logistic-science-pack",
        )

        val productivity_module_limitation_lvl1 = setOf(
            "chemical-science-pack",
            "military-science-pack",
            "engine-unit",
            "iron-stick",
            "iron-gear-wheel",
            "flying-robot-frame"

        )

        val productivity_module_limitation = setOf(
//            "iron-stick",
//            "iron-gear-wheel",
//            "engine-unit",
//            "flying-robot-frame",
//            "chemical-science-pack",
//            "military-science-pack",

            "iron-plate",
            "copper-plate",
            "steel-plate",
            "steel-plate-fixed",

            "empty-barrel",
            "uranium-processing",
            "copper-cable",
            "advanced-circuit",
            "electronic-circuit",
            "processing-unit",
            "uranium-fuel-cell",
            "explosives",
            "low-density-structure",
            "nuclear-fuel",
            "nuclear-fuel-reprocessing",
            "rocket-control-unit",
            "space-science-pack",
            "production-science-pack",
            "utility-science-pack",
            "kovarex-enrichment-process",
            "electronic-circuit-fixed",
            "processing-unit-fixed",
        )

        val excluded_recipes = setOf(
            "solid-fuel-from-petroleum-gas",
            "solid-fuel-from-heavy-oil",
            "processing-unit",
            "electronic-circuit",
            "steel-plate"
        )

    }
}