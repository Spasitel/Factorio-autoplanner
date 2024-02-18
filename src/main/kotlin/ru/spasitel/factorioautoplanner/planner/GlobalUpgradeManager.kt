package ru.spasitel.factorioautoplanner.planner

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.Field
import ru.spasitel.factorioautoplanner.data.ProcessedItem
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.*
import kotlin.math.abs

class GlobalUpgradeManager(private val globalPlanner: GlobalPlanner) {
    private val scoreManager = ScoreManager()
    private val logger = KotlinLogging.logger {}

    fun upgradeProductivity(current: State, recipeTree: Map<String, ProcessedItem>, field: Field): State {
        val best = bestFill(current, recipeTree, field, limit = 10)
        logger.info { "After fill:" }
        logger.info { Utils.convertToJson(best) }
        scoreManager.calculateScore(best, recipeTree, true)
        val best1 = removeOne(best, recipeTree, field)


        return best
    }

    private fun removeOne(start: State, recipeTree: Map<String, ProcessedItem>, field: Field): State {
        var best = start
        var changed = true
        var attempts = 0
        loop@ while (changed) {
            val toRemove = listToRemove(best, field).toMutableList()
            logger.info { "Remove one attempt: $attempts" }
            attempts++
            changed = false
            while (toRemove.size >= 2) {
                val building = toRemove.removeAt(0)
                val building2 = toRemove.removeAt(0)
                logger.info { "Remove one: $building, $building2" }
                val removed1 = removeWithConnectors(best, building)
                val removed = removeWithConnectors(removed1, building2)
                val current = bestFill(removed, recipeTree, field)
                if (isDifferent(current, best, building)) {
                    changed = true
                    val newScore = scoreManager.calculateScore(current, recipeTree, false)
                    logger.info { "Remove one update best ${newScore.second}: ${newScore.first}" }
                    logger.info { Utils.convertToJson(current) }
                    best = current
                }
            }
        }
        return best
    }

    private fun listToRemove(best: State, field: Field): List<Building> {
        val skip = setOf(
            "copper-cable#green",
            "copper-cable#blue",
            "electronic-circuit#blue",
            "electronic-circuit",
            "processing-unit",
        )
        val notTouch = best.buildings.filter { it.type == BuildingType.ASSEMBLER && (it as Assembler).recipe in skip }
        return best.buildings
            .asSequence()
            .filter { it.type == BuildingType.BEACON || it.type == BuildingType.ASSEMBLER || it.type == BuildingType.SMELTER }
            .filter { field.state.map[it.place.start] == null }
            .filter { it.type != BuildingType.ASSEMBLER || (it as Assembler).recipe !in skip }
            .filter {
                it.type != BuildingType.BEACON ||
                        !notTouch.any { a -> abs(a.place.start.x - it.place.start.x) < 6 && abs(a.place.start.y - it.place.start.y) < 6 }
            }
            .sortedBy { it.place.start.x * best.size.y + it.place.start.y }
            .toList()
    }

    private fun isDifferent(newBest: State, best: State, building: Building): Boolean {
        if (newBest.map[building.place.start] == null) return true
        if (newBest.map[building.place.start]!!.type != building.type) return true
        if (newBest.map[building.place.start] is Assembler && building is Assembler) {
            if ((newBest.map[building.place.start] as Assembler).recipe != building.recipe) return true
        }
        if (newBest.map[building.place.start]!!.place.start != building.place.start) return true

        if (building.type == BuildingType.BEACON) return false

        val from = best.buildings.filterIsInstance<Inserter>()
            .filter { it.from() in building.place.cells }
        val formNew = newBest.buildings.filterIsInstance<Inserter>()
            .filter { it.from() in building.place.cells }
        if (from.size != formNew.size || formNew.size != 1) throw IllegalStateException("Wrong size")
        if (from[0].place.start != formNew[0].place.start) return true

        val to = best.buildings.filterIsInstance<Inserter>()
            .filter { it.to() in building.place.cells }
        val toNew = newBest.buildings.filterIsInstance<Inserter>()
            .filter { it.to() in building.place.cells }
        if (to.size != toNew.size || toNew.size != 1) throw IllegalStateException("Wrong size")
        if (to[0].place.start != toNew[0].place.start) return true

        return false
    }


    private fun bestFill(
        current: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        limit: Int = 9999
    ): State {
        var best = current
        while (true) {
            val min = scoreManager.calculateScore(best, recipeTree, false)
            val assembler = best.buildings.filterIsInstance<Assembler>().size
            val smelter = best.buildings.filterIsInstance<Smelter>().size
            val beacon = best.buildings.filterIsInstance<Beacon>().size
            logger.info { "Updating ${min.second}, ${min.first}. Map counts: $assembler, $smelter, $beacon" }
            logger.trace { Utils.convertToJson(best) }
            val special = min.second in setOf("electronic-circuit", "processing-unit")
            best = globalPlanner.planeSingleStep(
                min.first,
                best,
                field,
                min.second,
                recipeTree,
                mutableSetOf(),
                globalPlanner::stepUnit,
                limit = limit,
                special = special
            ).first ?: break
        }
        return best
    }


    private fun removeWithConnectors(
        newBest: State,
        building: Building
    ): State {
        if (building.type == BuildingType.BEACON) {
            return newBest.removeBuilding(building)
        }
        val connections = newBest.buildings.filterIsInstance<Inserter>()
            .filter { it.to() in building.place.cells || it.from() in building.place.cells }
        if (connections.size != 2) throw IllegalStateException("Wrong size")
        val chests =
            connections.map { if (newBest.map[it.to()]!! is ProviderChest || newBest.map[it.to()]!! is RequestChest) newBest.map[it.to()]!! else newBest.map[it.from()]!! }
                .filter { chest ->
                    newBest.buildings.filterIsInstance<Inserter>()
                        .filter { it.to() in chest.place.cells || it.from() in chest.place.cells }.size == 1
                }
        var result = newBest.removeBuilding(building)
        for (chest in chests) {
            result = result.removeBuilding(chest)
        }
        for (connection in connections) {
            result = result.removeBuilding(connection)
        }
        return result
    }

}