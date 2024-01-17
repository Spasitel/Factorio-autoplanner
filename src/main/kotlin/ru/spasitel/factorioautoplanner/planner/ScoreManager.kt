package ru.spasitel.factorioautoplanner.planner

import ru.spasitel.factorioautoplanner.data.ProcessedItem
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.building.Assembler
import ru.spasitel.factorioautoplanner.data.building.Building
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import ru.spasitel.factorioautoplanner.data.building.ChemicalPlant

class ScoreManager {

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
        //TODO: green and blue circuits
        val buildings = buildingsForUnit(unit, state)
        val result = buildings.sumOf { calculateScoreForBuilding(Pair(state, it), unit) }
        return result / productivity
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
            .minOf { calculateScoreForItem(greedy, it, recipeTree) }
    }
}