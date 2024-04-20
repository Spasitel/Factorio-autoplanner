package ru.spasitel.factorioautoplanner.planner

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.ProcessedItem
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.*

class ScoreManager {
    private val logger = KotlinLogging.logger {}

    fun calculateScoreForItem(state: State, unit: String, recipeTree: Map<String, ProcessedItem>): Double {
        val recipe = recipeTree[unit]!!
        val productivity = recipe.productivity()
        return calculateScoreForItemWithProductivity(unit, state, productivity)
    }

    fun calculateScoreForItemWithProductivity(
        unit: String,
        state: State,
        productivity: Double
    ): Double {
        val buildings = buildingsForUnit(unit, state)
        val result = if (unit in setOf("electronic-circuit", "processing-unit", "rocket-fuel", "steel-plate")) {
            buildings.sumOf { calculateScoreForMultiBuildings(state, it, unit).first }
        } else {
            buildings.sumOf { calculateScoreForBuilding(Pair(state, it), unit) }
        }
        return result / productivity
    }

    fun calculateScoreForMultiBuildings(state: State, b: Building, unit: String): Pair<Double, Building> {
        var building = b
        var productivity = calculateScoreForBuilding(Pair(state, b), unit)
        when (unit) {
            "electronic-circuit" -> {
                val inserter = state.buildings.filterIsInstance<Inserter>()
                    .first { it.to() in building.place.cells && state.map[it.from()] is Assembler && (state.map[it.from()] as Assembler).recipe == "copper-cable#green" }
                val cable = (state.map[inserter.from()] as Assembler)
                val cableProductivity = calculateScoreForBuilding(Pair(state, cable), "copper-cable") * 0.93333332586
                if (cableProductivity < productivity) {
                    productivity = cableProductivity
                    building = cable
                }
            }

            "rocket-fuel" -> {
                val inserter = state.buildings.filterIsInstance<Inserter>()
                    .first { it.to() in building.place.cells && state.map[it.from()] is ChemicalPlant && (state.map[it.from()] as ChemicalPlant).recipe == "solid-fuel-from-light-oil" }
                val cable = (state.map[inserter.from()] as ChemicalPlant)
                val cableProductivity = calculateScoreForBuilding(Pair(state, cable), "solid-fuel-from-light-oil") * 1.2
                if (cableProductivity < productivity) {
                    productivity = cableProductivity
                    building = cable
                }
            }

            "processing-unit" -> {
                val inserter = state.buildings.filterIsInstance<Inserter>()
                    .first { it.to() in building.place.cells && state.map[it.from()] is Assembler && (state.map[it.from()] as Assembler).recipe == "electronic-circuit#blue" }
                val green = (state.map[inserter.from()] as Assembler)
                val greenProductivity = calculateScoreForBuilding(Pair(state, green), "electronic-circuit") * 1.4
                if (greenProductivity < productivity) {
                    productivity = greenProductivity
                    building = green
                }

                val inserter2 = state.buildings.filterIsInstance<Inserter>()
                    .firstOrNull { it.to() in green.place.cells && state.map[it.from()] is Assembler && (state.map[it.from()] as Assembler).recipe == "copper-cable#blue" }
                val cable = (state.map[inserter2!!.from()] as Assembler)
                val cableProductivity = calculateScoreForBuilding(Pair(state, cable), "copper-cable") * 1.30666666
                if (cableProductivity < productivity) {
                    productivity = cableProductivity
                    building = cable
                }
            }

            "steel-plate" -> {
                val inserter = state.buildings.filterIsInstance<Inserter>()
                    .first { it.to() in building.place.cells && state.map[it.from()] is Smelter }
                val cable = (state.map[inserter.from()] as Smelter)
                val cableProductivity = calculateScoreForBuilding(Pair(state, cable), "iron-plate")
                if (cableProductivity < productivity) {
                    productivity = cableProductivity
                    building = cable
                }
            }

        }

        return Pair(productivity, building)
    }

    fun buildingsForUnit(
        unit: String,
        state: State
    ) = when (unit) {
        "crude-oil" -> state.buildings.filterIsInstance<OilRefinery>()
        "heavy-oil" -> state.buildings.filter { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == "heavy-oil-cracking" }
        "light-oil" -> state.buildings.filter { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == "light-oil-cracking" }
        "solid-fuel" -> state.buildings.filter { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == "solid-fuel-from-light-oil" }
        "battery", "plastic-bar", "sulfuric-acid", "sulfur", "lubricant" -> state.buildings.filter { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == unit }
        "stone-brick", "copper-plate", "iron-plate" -> state.buildings.filterIsInstance<Smelter>()
        "space-science-pack" -> state.buildings.filterIsInstance<RocketSilo>()
        "science-approximation" -> state.buildings.filterIsInstance<Lab>()
        "steel-plate" -> state.buildings.filter { it.type == BuildingType.SMELTER && (it as Smelter).recipe == "steel-plate" }

        else -> state.buildings.filter { b -> b.type == BuildingType.ASSEMBLER && (b as Assembler).recipe == unit }
    }

    /**
     * Продуктивность здания
     */
    fun calculateScoreForBuilding(pair: Pair<State, Building>, unit: String): Double {
        val building = pair.second
        when (building.type) {
            BuildingType.ASSEMBLER -> {
                var start = (building as Assembler).speed()
                start += pair.first.performanceMap.getOrDefault(building.place.start, 0.0)
                return start
            }

            BuildingType.CHEMICAL_PLANT -> {
                var start =
                    if (unit in TechnologyTreePlanner.productivity_module_limitation) { //todo: minor. module lvl
                        0.55
                    } else {
                        (building as ChemicalPlant).speed()
                    }
                start += pair.first.performanceMap.getOrDefault(building.place.start, 0.0)
                return start
            }

            BuildingType.BEACON -> {
                return buildingsForUnit(unit, pair.first).filter {
                    Utils.isBeaconAffect(building, it)
                }.size * (building as Beacon).speed()
            }

            BuildingType.SMELTER -> {
                var start =
                    if (unit in TechnologyTreePlanner.productivity_module_limitation) { //todo: minor. module lvl
                        0.7
                    } else {
                        1.4 //2.0
                    }
                start += pair.first.performanceMap.getOrDefault(building.place.start, 0.0)
                return start
            }

            BuildingType.ROCKET_SILO -> {
                var start = 0.4
                start +=
                    pair.first.buildings.filterIsInstance<Beacon>().filter {
                        Utils.isBeaconAffect(it, building)
                    }.sumOf { it.speed() }
                return start
            }

            BuildingType.OIL_REFINERY -> {
                var start = (building as OilRefinery).speed()
                start +=
                    pair.first.buildings.filterIsInstance<Beacon>().filter {
                        Utils.isBeaconAffect(it, building)
                    }.sumOf { it.speed() }
                return start
            }

            BuildingType.LAB -> {
                var start = 0.7
                start += pair.first.performanceMap.getOrDefault(building.place.start, 0.0)
                return start
            }

            else -> throw RuntimeException("Unknown building type: ${building.type}")
        }
    }

    fun calculateScore(greedy: State, recipeTree: Map<String, ProcessedItem>, log: Boolean): Pair<Double, String> {
        return scoreMap(greedy, recipeTree, log).minBy { it.first }
    }

    fun scoreMap(
        greedy: State,
        recipeTree: Map<String, ProcessedItem>,
        log: Boolean
    ) = recipeTree.keys.filter { it !in TechnologyTreePlanner.base }
        .map {
            val score = calculateScoreForItem(greedy, it, recipeTree)
            if (log) {
                val buildings = buildingsForUnit(it, greedy).size
                logger.debug { "For $it: buildings $buildings score $score" }
            }
            Pair(score, it)
        }
}