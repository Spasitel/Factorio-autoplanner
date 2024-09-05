package ru.spasitel.factorioautoplanner.planner

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.Field
import ru.spasitel.factorioautoplanner.data.ProcessedItem
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.*

class RoboportsManager {
    private val logger = KotlinLogging.logger {}
    private val scoreManager = ScoreManager()

    fun planeRoboports(state: State, field: Field, recipeTree: Map<String, ProcessedItem>): Double {
        // calculate provider chests with amount of items
        val providers = calculateProviderChests(state, field, recipeTree)
        // calculate request chests with amount of items
        val requests = calculateRequestChests(state, field, recipeTree)
        // calculate number of roboports
        val sum = calculateRoboports(providers, requests)


        return sum
    }

    private fun calculateRoboports(
        providers: Map<String, Set<Pair<ProviderChest, Double>>>,
        requests: Map<String, Set<Pair<RequestChest, Double>>>
    ): Double {
        val result: MutableMap<String, Double> = mutableMapOf()
        for (request in requests) {
            val item = request.key
            result[item] = 0.0
            if (providers[item] == null) {
                logger.info { "No providers for $item" }
                continue
            }
            val providersAmount = providers[item]!!.sumOf { it.second }
            request.value.forEach { (chest, amount) ->
                providers[item]!!.forEach { (provider, providerAmount) ->
                    val distance = chest.place.start.distanceTo(provider.place.start)
                    val d = amount * providerAmount * distance / providersAmount / 4.0
                    result[item] = result[item]!! + d
                }
            }
        }
        return result.map { it.value }.sum()
    }

    fun calculateRequestChests(
        state: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>
    ): Map<String, Set<Pair<RequestChest, Double>>> {
        val result = mutableMapOf<String, MutableSet<Pair<RequestChest, Double>>>()
        for (request in state.buildings.filterIsInstance<RequestChest>()) {
            if (!Utils.isBetween(request.place.start, field.chestField)) {
                continue
            }

            val inserters =
                state.buildings.filter { it.type == BuildingType.INSERTER && (it as Inserter).from() == request.place.start }
            for (inserter in inserters) {
                var source = state.map[(inserter as Inserter).to()]!!
                if (source.type == BuildingType.STEEL_CHEST) {
                    val inserter2 =
                        state.buildings.first { it.type == BuildingType.INSERTER && (it as Inserter).from() == source.place.start }
                    source = state.map[(inserter2 as Inserter).to()]!!
                }
                if (source.type == BuildingType.ROBOPORT) {
                    continue
                }
                val item = when (source) {
                    is Assembler -> source.recipe
                    is ChemicalPlant -> source.recipe
                    is Smelter -> source.recipe
                    is RocketSilo -> "space-science-pack"
                    is Lab -> "science-approximation"
                    else -> throw IllegalStateException("Unknown source $source")
                }
                val recipe = recipeTree[item.split("#")[0]]!!
                for (ingredient in recipe.ingredients) {
                    if (ingredient.key in setOf("petroleum-gas", "lubricant", "sulfuric-acid", "water")) {
                        continue
                    }
                    //green and blue - do not request what made in chain
                    if ((ingredient.key == "iron-plate" || ingredient.key == "copper-plate") && item == "processing-unit") {
                        continue
                    }
                    if (ingredient.key == "copper-plate" && item.split("#")[0] == "electronic-circuit") {
                        continue
                    }
                    if (ingredient.key == "sulfur" && item == "sulfuric-acid") {
                        continue
                    }
                    val set = result.getOrDefault(ingredient.key, mutableSetOf())
                    val amount = calculateRequestAmount(source, state, recipeTree, item, ingredient.key)
                    set.add(Pair(request, amount))
                    result[ingredient.key] = set
                }
            }
        }
        return result
    }

    private fun calculateRequestAmount(
        source: Building,
        state: State,
        recipeTree: Map<String, ProcessedItem>,
        item: String,
        key: String
    ): Double {
        val productivityOut = calculateProviderAmount(source, state, recipeTree, item)
        val recipe = recipeTree[item.split("#")[0]]!!
        return productivityOut / recipe.amount * recipe.ingredients[key]!!
    }

    fun calculateProviderChests(
        state: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>
    ): Map<String, Set<Pair<ProviderChest, Double>>> {
        val result = mutableMapOf<String, MutableSet<Pair<ProviderChest, Double>>>()
        for (provider in state.buildings.filterIsInstance<ProviderChest>()) {
            if (!Utils.isBetween(provider.place.start, field.chestField)) {
                continue
            }
            if (provider.items.isEmpty()) {
                throw IllegalStateException("Provider chest without items")
            }
            if (provider.items.first() in TechnologyTreePlanner.base) {
                val item = provider.items.first()
                val set = result.getOrDefault(item, mutableSetOf())
                set.add(Pair(provider, 100.0))
                result[item] = set
                continue
            }
            val inserters =
                state.buildings.filter { it.type == BuildingType.INSERTER && (it as Inserter).to() == provider.place.start }
            for (inserter in inserters) {
                var source = state.map[(inserter as Inserter).from()]!!
                if (source.type == BuildingType.STEEL_CHEST) {
                    val inserter2 =
                        state.buildings.first { it.type == BuildingType.INSERTER && (it as Inserter).to() == source.place.start }
                    source = state.map[(inserter2 as Inserter).from()]!!
                }
                val item = when (source) {
                    is Assembler -> source.recipe
                    is ChemicalPlant -> source.recipe
                    is Smelter -> source.recipe
                    is RocketSilo -> "space-science-pack"
                    is RequestChest -> continue
                    else -> throw IllegalStateException("Unknown source $source")
                }
                val set = result.getOrDefault(item, mutableSetOf())
                val amount = calculateProviderAmount(source, state, recipeTree, item)
                set.add(Pair(provider, amount))
                result[item] = set
            }
        }
        return result
    }

    private fun calculateProviderAmount(
        source: Building,
        state: State,
        recipeTree: Map<String, ProcessedItem>,
        item: String
    ): Double {
        val productivity = scoreManager.calculateScoreForBuilding(Pair(state, source), item)
        val recipe = recipeTree[item.split("#")[0]]!!
        return productivity / recipe.totalProductivity
    }

}