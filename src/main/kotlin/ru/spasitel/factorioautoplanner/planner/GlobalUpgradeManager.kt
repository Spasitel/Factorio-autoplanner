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
    private val roboportsManager = RoboportsManager()

    fun upgradeProductivity(current: State, recipeTree: Map<String, ProcessedItem>, field: Field): State {
        val best = bestFill(current, recipeTree, field, limit = 10)
        logger.info { "After fill:" }
        logger.info { Utils.convertToJson(best) }
        scoreManager.calculateScore(best, recipeTree, true)
        val bestOne = removeOne(best, recipeTree, field)
        logger.info { "After remove one:" }
        logger.info { Utils.convertToJson(bestOne) }
        scoreManager.calculateScore(bestOne, recipeTree, true)

        val bestTwo = removeTwo(bestOne, recipeTree, field)
        logger.info { "After remove two:" }
        logger.info { Utils.convertToJson(bestTwo) }
        scoreManager.calculateScore(bestTwo, recipeTree, true)


        return bestTwo
    }

    private fun removeOne(start: State, recipeTree: Map<String, ProcessedItem>, field: Field): State {
        var best = start
        var bestScore = 0.0
        var changed = true
        var attempts = 0
        var same = 0
        loop@ while (changed) {
            val toRemove = listToRemove(best, field).toMutableList()
            logger.info { "Remove one attempt: $attempts" }
            val score = scoreManager.calculateScore(best, recipeTree, false).first
            if (score > bestScore) {
                same = 0
                bestScore = score
                logger.info { "Remove one New best score: $bestScore" }
            } else if (same > 3) {
                return best
            } else {
                same++
            }
            attempts++
            changed = false
            var localBestScore = bestScore
            while (toRemove.isNotEmpty()) {
                val building = toRemove.removeAt(0)
                logger.info { "Remove one: $building" }
                val removed = removeWithConnectors(best, building)
                val current = bestFillAll(removed, recipeTree, field)
                if (isDifferent(current, best, building)) {
                    changed = true
                    val newScore = scoreManager.calculateScore(current, recipeTree, false)
                    logger.info { "Remove one update best ${newScore.second}: ${newScore.first}" }
                    logger.info { Utils.convertToJson(current) }
                    if (newScore.first > localBestScore) {
                        localBestScore = newScore.first
                    } else if (newScore.first < localBestScore) {
                        logger.error { "Remove one problem $localBestScore to ${newScore.first}" }
                        logger.info { Utils.convertToJson(removed) }
                        logger.info { Utils.convertToJson(best) }
                        val again = bestFillAll(removed, recipeTree, field)
                        logger.info { Utils.convertToJson(again) }
                    }
                    best = current
                } else {
                    logger.info { "Remove one not different" }
                }
            }
        }
        return best
    }

    private fun removeTwo(start: State, recipeTree: Map<String, ProcessedItem>, field: Field): State {
        var best = start
        var bestScore = 0.0
        var attempts = 0
        var same = 0
        while (true) {
            val toRemove = listToRemove(best, field).toMutableList()
            val toRemoveSet = getPairsToRemove(toRemove, attempts)
            logger.info { "Remove two attempt: $attempts" }
            val score = scoreManager.calculateScore(best, recipeTree, false).first
            if (score > bestScore) {
                same = 0
                bestScore = score
                logger.info { "New remove two best score: $bestScore" }
            } else if (same > toRemove.size) {
                return best
            } else {
                same++
            }
            attempts++
            var localBestScore = bestScore
            while (toRemoveSet.isNotEmpty()) {
                val (building, building2) = toRemoveSet.removeAt(0)
                logger.info { "Remove two: $building, $building2" }

                val removed1 = removeWithConnectors(best, building)
                val removed = removeWithConnectors(removed1, building2)
                val current = bestFillAll(removed, recipeTree, field)

                val newScore = scoreManager.calculateScore(current, recipeTree, false)
                logger.info { "Remove two update best ${newScore.second}: ${newScore.first}" }
                logger.info { Utils.convertToJson(current) }
                if (newScore.first > localBestScore) {
                    localBestScore = newScore.first
                } else if (newScore.first < localBestScore) {
                    logger.error { "Remove two problem $localBestScore to ${newScore.first}" }
                    logger.info { Utils.convertToJson(removed) }
                    logger.info { Utils.convertToJson(best) }
                    val again = bestFillAll(removed, recipeTree, field)
                    logger.info { Utils.convertToJson(again) }
                }
                best = current
            }
        }
    }

    fun getPairsToRemove(
        toRemove: MutableList<Building>,
        attempts: Int
    ): MutableList<Pair<Building, Building>> {
        val result = mutableListOf<Pair<Building, Building>>()
        val seed = attempts.mod(toRemove.size)
        for (i in 0 until seed.div(2)) {
            result.add(Pair(toRemove[i], toRemove[seed - i - 1]))
        }
        for (i in 0 until (toRemove.size - seed).div(2)) {
            result.add(Pair(toRemove[i + seed], toRemove[toRemove.size - i - 1]))
        }
        if (toRemove.size.mod(2) == 0 && seed.mod(2) == 1) {
            result.add(Pair(toRemove[seed.div(2)], toRemove[(toRemove.size + seed).div(2)]))
        }

        return result
    }

    private fun listToRemove(
        best: State,
        field: Field,
        removeBeacons: Boolean = true,
        includeScip: Boolean = false
    ): List<Building> {
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
            .filter { (it.type == BuildingType.BEACON && removeBeacons) || it.type == BuildingType.ASSEMBLER || it.type == BuildingType.SMELTER }
            .filter { field.state.map[it.place.start] == null }
            .filter { includeScip || it.type != BuildingType.ASSEMBLER || (it as Assembler).recipe !in skip }
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
//            logger.trace { Utils.convertToJson(best) }
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

    private fun bestFillAll(
        start: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        limit: Int = 9999
    ): State {
        var best = start
        var bestScore = 0.0
        val todo = mutableSetOf(start)
        while (todo.isNotEmpty()) {
            val current = todo.maxBy { it.buildings.size }
            todo.remove(current)


            val min = scoreManager.calculateScore(current, recipeTree, false)
            val assembler = current.buildings.filterIsInstance<Assembler>().size
            val smelter = current.buildings.filterIsInstance<Smelter>().size
            val beacon = current.buildings.filterIsInstance<Beacon>().size
            logger.info { "Updating ${min.second}, ${min.first}. Map counts (b/a/s): $beacon, $assembler, $smelter. Todo ${todo.size}" }
//            logger.trace { Utils.convertToJson(current) }
            if (min.first > bestScore ||
                (min.first == bestScore && current.freeCells.size > best.freeCells.size) ||
                (min.first == bestScore && current.freeCells.size == best.freeCells.size && current.emptyCountScore > best.emptyCountScore)
            ) {
                logger.info { "New best: ${min.first}" }
                best = current
                bestScore = min.first
            }

            val special = min.second in setOf("electronic-circuit", "processing-unit")
            val next = globalPlanner.planeAllSingleStep(
                min.first,
                current,
                field,
                min.second,
                recipeTree,
                mutableSetOf(),
                globalPlanner::stepAllUnit,
                limit = limit,
                special = special
            )
            next.forEach { todo.add(it.second) }
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
            .filter {
                (it.to() in building.place.cells && newBest.map[it.from()] is RequestChest) ||
                        (it.from() in building.place.cells && newBest.map[it.to()] is ProviderChest)
            }
        if (connections.size != 2 &&
            !(connections.size == 1 &&
                    building.type == BuildingType.ASSEMBLER &&
                    (building as Assembler).recipe.contains("#"))
        ) throw IllegalStateException("Wrong size ${connections.size} for $building")
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

    fun upgradeRobotsTwo(productivity: State, recipeTree: Map<String, ProcessedItem>, field: Field): State {
        //do not move assemblers and beacons and smelters
        //move chests and inserters
        //swap assemblers recipes - if not reducing productivity
        //swap electronic circuit recipes - if not reducing productivity
        val productionScore = scoreManager.calculateScore(productivity, recipeTree, false)
        var best = productivity
        var bestScore = roboportsManager.planeRoboports(productivity, field, recipeTree)
        var attempts = 0
        var same = 0
        while (true) {
            val toRemove = listToRemove(best, field, removeBeacons = false).toMutableList()
            val toRemoveSet = getPairsToRemove(toRemove, attempts)
            logger.info { "Upgrade roboports two attempt: $attempts" }
            val score = roboportsManager.planeRoboports(best, field, recipeTree)
            if (score < bestScore) {
                same = 0
                bestScore = score
                logger.info { "New Upgrade roboports two best score: $bestScore" }
            } else if (same > toRemove.size) {
                return best
            } else {
                same++
            }
            attempts++
            var localBestScore = bestScore
            while (toRemoveSet.isNotEmpty()) {
                val (building, building2) = toRemoveSet.removeAt(0)
                if (building.type == BuildingType.SMELTER && building2.type == BuildingType.SMELTER) continue
                if (building.type == BuildingType.ASSEMBLER && building2.type == BuildingType.ASSEMBLER &&
                    (building as Assembler).recipe == (building2 as Assembler).recipe
                ) continue

                logger.info { "Upgrade roboports two: $building, $building2" }

                val removed1 = removeWithConnectors(best, building)
                val removed = removeWithConnectors(removed1, building2)

                val recipe1 = if (building2 is Assembler) building2.recipe else "stone-brick"
                val newBuilding1 = Utils.getBuilding(
                    building.place.start,
                    building2.type,
                    recipe = recipe1
                )
                val add1 = removed.addBuilding(
                    newBuilding1
                )
                val recipe2 = if (building is Assembler) building.recipe else "stone-brick"
                val newBuilding2 = Utils.getBuilding(
                    building2.place.start,
                    building.type,
                    recipe = recipe2
                )
                val add2 = add1!!.addBuilding(
                    newBuilding2
                )!!

                if (scoreManager.calculateScoreForItem(add2, recipe1, recipeTree) < productionScore.first ||
                    scoreManager.calculateScoreForItem(add2, recipe2, recipeTree) < productionScore.first
                ) {
                    logger.info { "Upgrade roboports two scip: $building, $building2" }
                    continue
                }

                globalPlanner.planeChests(add2, field, newBuilding1, recipe1).forEach { chest1 ->
                    globalPlanner.planeChests(chest1, field, newBuilding2, recipe2).forEach { current ->
                        val newScore = roboportsManager.planeRoboports(current, field, recipeTree)
                        if (newScore < localBestScore) {
                            logger.info { "Upgrade roboports two update best $newScore" }
                            logger.info { Utils.convertToJson(current) }
                            localBestScore = newScore
                            best = current
                        }
                    }
                }
            }
        }

    }

    fun upgradeRobots(start: State, recipeTree: Map<String, ProcessedItem>, field: Field): State {
        var best = start
        var bestScore = roboportsManager.planeRoboports(start, field, recipeTree)
        var attempts = 0
        var same = 0
        while (true) {
            val toRemove = listToRemove(best, field, removeBeacons = false, includeScip = true).toMutableList()
            logger.info { "Upgrade roboports attempt: $attempts" }
            val score = roboportsManager.planeRoboports(best, field, recipeTree)
            if (score < bestScore) {
                same = 0
                bestScore = score
                logger.info { "New Upgrade roboports best score: $bestScore" }
            } else if (same > 3) {
                return best
            } else {
                same++
            }
            attempts++
            var localBestScore = bestScore
            while (toRemove.isNotEmpty()) {
                val building = toRemove.removeAt(0)

                logger.info { "Upgrade roboports: $building" }

                val removed = removeWithConnectors(best, building)

                val add = removed.addBuilding(building)!!
                val recipe = if (building is Assembler) building.recipe else "stone-brick"

                globalPlanner.planeChests(add, field, building, recipe).forEach { current ->
                    val newScore = roboportsManager.planeRoboports(current, field, recipeTree)
                    if (newScore < localBestScore) {
                        logger.info { "Upgrade roboports update best $newScore" }
                        logger.info { Utils.convertToJson(current) }
                        localBestScore = newScore
                        best = current
                    }
                }
            }
        }
    }

    fun downgradeProductivity(upgrade: State, recipeTree: Map<String, ProcessedItem>, fieldPrep: Field): State {


        TODO("Not yet implemented")
    }

}