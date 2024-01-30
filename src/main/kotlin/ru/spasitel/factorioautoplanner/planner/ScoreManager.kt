package ru.spasitel.factorioautoplanner.planner

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.ProcessedItem
import ru.spasitel.factorioautoplanner.data.State
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
        val result = if (unit in setOf("electronic-circuit", "processing-unit", "rocket-fuel")) {
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
                    .first { it.to() in green.place.cells && state.map[it.from()] is Assembler && (state.map[it.from()] as Assembler).recipe == "copper-cable#blue" }
                val cable = (state.map[inserter2.from()] as Assembler)
                val cableProductivity = calculateScoreForBuilding(Pair(state, cable), "copper-cable") * 1.30666666
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
        "crude-oil" -> state.buildings.filter { it.type == BuildingType.OIL_REFINERY }
        "heavy-oil" -> state.buildings.filter { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == "heavy-oil-cracking" }
        "light-oil" -> state.buildings.filter { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == "light-oil-cracking" }
        "solid-fuel" -> state.buildings.filter { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == "solid-fuel-from-light-oil" }
        "battery", "plastic-bar", "sulfuric-acid", "sulfur", "lubricant" -> state.buildings.filter { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == unit }
        "stone-brick" -> state.buildings.filter { it.type == BuildingType.SMELTER }
        "space-science-pack" -> state.buildings.filter { it.type == BuildingType.ROCKET_SILO }
        "science-approximation" -> state.buildings.filter { it.type == BuildingType.LAB }
        else -> state.buildings.filter { b -> b.type == BuildingType.ASSEMBLER && (b as Assembler).recipe == unit }
    }

    /**
     * Продуктивность здания
     */
    fun calculateScoreForBuilding(pair: Pair<State, Building>, unit: String): Double {
        val building = pair.second
        when (building.type) {
            BuildingType.ASSEMBLER -> {
                var start = if (unit in TechnologyTreePlanner.productivity_module_limitation) {
                    0.4
                } else {
                    3.0
                }
                start += pair.first.performanceMap.getOrDefault(building.place.start, 0) * 0.5
                return start
            }

            BuildingType.CHEMICAL_PLANT -> {
                var start = if (unit in TechnologyTreePlanner.productivity_module_limitation) {
                    0.55
                } else {
                    2.5
                }
                start += pair.first.performanceMap.getOrDefault(building.place.start, 0) * 0.5
                return start
            }

            BuildingType.BEACON -> {
                return pair.first.buildings.filter {
                    it.type == BuildingType.ASSEMBLER &&
                            (it as Assembler).recipe == unit
                            && it.place.start.maxDistanceTo(building.place.start) < 6
                }.size * 0.5
            }

            BuildingType.SMELTER -> {
                var start = if (unit in TechnologyTreePlanner.productivity_module_limitation) {
                    0.7
                } else {
                    2.0
                }
                start += pair.first.performanceMap.getOrDefault(building.place.start, 0) * 0.5
                return start
            }

            BuildingType.ROCKET_SILO -> {
                var start = 0.4
                start +=
                    pair.first.buildings.filter {
                        val max = BuildingType.ROCKET_SILO.size + 2
                        it.type == BuildingType.BEACON &&
                                it.place.start.x - building.place.start.x in -5..max &&
                                it.place.start.y - building.place.start.y in -5..max
                    }.size * 0.5
                return start
            }

            BuildingType.OIL_REFINERY -> {
                var start = 2.5
                start +=
                    pair.first.buildings.filter {
                        val max = BuildingType.OIL_REFINERY.size + 2
                        it.type == BuildingType.BEACON &&
                                it.place.start.x - building.place.start.x in -5..max &&
                                it.place.start.y - building.place.start.y in -5..max
                    }.size * 0.5
                return start
            }

            BuildingType.LAB -> {
                var start = 0.7
                start += pair.first.performanceMap.getOrDefault(building.place.start, 0) * 0.5
                return start
            }

            else -> throw RuntimeException("Unknown building type: ${building.type}")
        }
    }

    fun calculateScore(greedy: State, recipeTree: Map<String, ProcessedItem>): Double {
        return recipeTree.keys.filter { it !in TechnologyTreePlanner.base }
            .minOf {
                val score = calculateScoreForItem(greedy, it, recipeTree)
                val buildings = buildingsForUnit(it, greedy).size
                logger.debug { "For $it: buildings $buildings score $score" }
                return@minOf score
            }
    }
}