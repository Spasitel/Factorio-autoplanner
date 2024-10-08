package ru.spasitel.factorioautoplanner.planner

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.*
import ru.spasitel.factorioautoplanner.data.building.*
import kotlin.math.min

private const val roboport_timeout = 1000 * 60 * 60 * 8

class GlobalUpgradeManager(private val globalPlanner: GlobalPlanner) {
    private val scoreManager = ScoreManager()
    private val logger = KotlinLogging.logger {}
    private val roboportsManager = RoboportsManager()

    fun upgradeProductivity(current: State, recipeTree: Map<String, ProcessedItem>, field: Field): State {
        val best = bestFill(current, recipeTree, field, limit = 5)
        logger.info { "After fill:" }
        logger.info { Utils.convertToJson(best) }
        scoreManager.calculateScore(best, recipeTree, true)
//        if (GlobalPlanner.isSmelter && GlobalPlanner.smelterType == "steel") {
//            return best
//        }
        val bestOne = removeOne(best, recipeTree, field)
        logger.info { "After remove one:" }
        logger.info { Utils.convertToJson(bestOne) }
        scoreManager.calculateScore(bestOne, recipeTree, true)

//        if (GlobalPlanner.isSmelter) {
        return bestOne
//        }
//        val bestTwo = removeTwo(bestOne, recipeTree, field)
//        logger.info { "After remove two:" }
//        logger.info { Utils.convertToJson(bestTwo) }
//        scoreManager.calculateScore(bestTwo, recipeTree, true)
//
//
//        return bestTwo
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
                if (!best.buildings.contains(building)) {
                    logger.info { "Remove one already removed $building" }
                    continue
                }
                logger.info { "Remove one: $building" }
                val removed = removeWithConnectors(best, building)
                val current = bestFillAll(removed, recipeTree, field, building)
//                if (isDifferent(current, best, building)) {
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
                    val again = bestFillAll(removed, recipeTree, field, removed = building)
                    logger.info { Utils.convertToJson(again) }
                }
                best = current
//                } else {
//                    logger.info { "Remove one not different" }
//                }
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
                if (GlobalPlanner.isSmelter && building.place.start.maxDistanceTo(building2.place.start) > 5) {
                    logger.info { "Remove two too far" }
                    continue
                }

                val removed1 = removeWithConnectors(best, building)
                val removed = removeWithConnectors(removed1, building2)
                val current = bestFillAll(removed, recipeTree, field, removed = building)

                val newScore = scoreManager.calculateScore(current, recipeTree, false)
                logger.info { "Remove two update best ${newScore.second}: ${newScore.first}" }
                logger.info { Utils.convertToJson(current) }
                if (newScore.first > localBestScore) {
                    localBestScore = newScore.first
                } else if (newScore.first < localBestScore) {
                    logger.error { "Remove two problem $localBestScore to ${newScore.first}" }
                    logger.info { Utils.convertToJson(removed) }
                    logger.info { Utils.convertToJson(best) }
                    val again = bestFillAll(removed, recipeTree, field, removed = building)
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
            "steel-plate",
            "iron-plate#steel"
        )
        val notTouch = best.buildings.filter { it.type == BuildingType.ASSEMBLER && (it as Assembler).recipe in skip }
        return best.buildings
            .asSequence()
            .filter { (it.type == BuildingType.BEACON && removeBeacons) || it.type == BuildingType.ASSEMBLER || it.type == BuildingType.SMELTER }
            .filter { field.state.map[it.place.start] == null }
            .filter { includeScip || it.type != BuildingType.ASSEMBLER || (it as Assembler).recipe !in skip }
            .filter { includeScip || it.type != BuildingType.SMELTER || (it as Smelter).recipe !in skip }
            .filter {
                it.type != BuildingType.BEACON ||
                        !notTouch.any { a -> Utils.isBeaconAffect(it, a) }
            }
//            .sortedBy { it.place.start.x * best.size.y + it.place.start.y }
//            .sortedBy { best.removeBuilding(it).emptyCountScore }
            .sortedBy { -removeWithConnectors(best, it).emptyCountScore }
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
        if (from.size != formNew.size || formNew.size != 1) {
            throw IllegalStateException("Wrong size")
        }
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
        val scip = mutableSetOf<Cell>()
        val freeStart = current.freeCells.size
        while (true) {
            val min = scoreManager.calculateScore(best, recipeTree, false)
            val assembler = best.buildings.filterIsInstance<Assembler>().size
            val smelter = best.buildings.filterIsInstance<Smelter>().size
            val beacon = best.buildings.filterIsInstance<Beacon>().size
            val freeCurrent = best.freeCells.size
            val progress = (freeStart - freeCurrent) / freeStart.toDouble() * 100.0
            logger.info { "Updating ${min.second}, ${min.first}. Progress: $progress. Map counts (a/s/b): $assembler, $smelter, $beacon" }
//            logger.trace { Utils.convertToJson(best) }
            val special = min.second in setOf("electronic-circuit", "processing-unit", "steel-plate")
            val stepUnit =
                if (GlobalPlanner.isSmelter && GlobalPlanner.smelterType == "steel")
                    globalPlanner::stepSteel
                else
                    globalPlanner::stepUnit
            best = globalPlanner.planeSingleStep(
                min.first,
                best,
                field,
                min.second,
                recipeTree,
                scip,
                stepUnit,
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
        removed: Building,
        limit: Int = 9999
    ): State {
        var best = start
        var bestScore = 0.0
        val todo = mutableSetOf(start)
        val done = mutableSetOf<SimpleState>()
        val scip = mutableSetOf<Cell>()

        while (todo.isNotEmpty()) {
            val current = todo.maxBy { it.buildings.size }
            todo.remove(current)
            val simple = SimpleState.fromState(current)
            if (simple in done) {
                logger.info { "Already done ${done.size}" }
                continue
            }
            done.add(simple)

            val min = scoreManager.calculateScore(current, recipeTree, false)
            val added =
                (current.buildings - start.buildings).filter { it.type == BuildingType.BEACON || it.type == BuildingType.ASSEMBLER || it.type == BuildingType.SMELTER }
            logger.info { "Updating ${min.second}, ${min.first}. Todo ${todo.size}. Trying $added" }

//            if (added.any { it.place.start.maxDistanceTo(removed.place.start) > 9 }) {
//                logger.info { "Too far" }
//                continue
//            }
            logger.trace { Utils.convertToJson(current) }
            if (min.first > bestScore ||
                (min.first == bestScore && current.freeCells.size > best.freeCells.size) ||
                (min.first == bestScore && current.freeCells.size == best.freeCells.size && current.emptyCountScore > best.emptyCountScore)
            ) {
                logger.info { "New best: ${min.first}" }
                best = current
                bestScore = min.first
            }

            if (done.size > 10000) {
                logger.info { "Too much attempts" }
                break
            }

            val special = min.second in setOf("electronic-circuit", "processing-unit", "steel-plate")
            val stepAllUnit = if (GlobalPlanner.isSmelter && GlobalPlanner.smelterType == "steel")
                globalPlanner::stepAllSteel
            else
                globalPlanner::stepAllUnit
            val next = globalPlanner.planeAllSingleStep(
                removed.place.start,
                current,
                field,
                min.second,
                recipeTree,
                scip,
                stepAllUnit,
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
        if (GlobalPlanner.isSmelter && GlobalPlanner.smelterType == "steel") {
            val smelter = building as Smelter
            val inserter = if (smelter.recipe == "steel-plate") {
                newBest.buildings.filterIsInstance<Inserter>()
                    .firstOrNull { it.to() in building.place.cells && newBest.map[it.from()] is Smelter }
            } else {
                newBest.buildings.filterIsInstance<Inserter>()
                    .firstOrNull { it.from() in building.place.cells && newBest.map[it.to()] is Smelter }
            }
            if (inserter != null) {
                var result = newBest.removeBuilding(inserter)
                result = removeWithConnectors(result, result.map[inserter.from()]!!)
                result = removeWithConnectors(result, result.map[inserter.to()]!!)
                return result
            }
        }
        val connections = newBest.buildings.filterIsInstance<Inserter>()
            .filter {
                (it.to() in building.place.cells && newBest.map[it.from()] is RequestChest) ||
                        (it.from() in building.place.cells && newBest.map[it.to()] is ProviderChest)
            }
        if (!GlobalPlanner.isSmelter && connections.size != 2) {
            if (connections.size != 1) {
                throw IllegalStateException("Wrong size ${connections.size} for $building")
            }
            if (building.type == BuildingType.ASSEMBLER && !(building as Assembler).recipe.contains("#")) {
                throw IllegalStateException("Wrong size ${connections.size} for $building")
            } else if (building.type == BuildingType.SMELTER &&
                !(building as Smelter).recipe.contains("#") && (building as Smelter).recipe != "steel-plate"
            ) {
                throw IllegalStateException("Wrong size ${connections.size} for $building")
            }

        }
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

    fun upgradeRobotsTwo(
        productivity: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        score: Double
    ): State {
        //do not move assemblers and beacons and smelters
        //move chests and inserters
        //swap assemblers recipes - if not reducing productivity
        //swap electronic circuit recipes - if not reducing productivity
        var productionScore = scoreManager.calculateScore(productivity, recipeTree, false)
        var best = productivity
        var bestScore = roboportsManager.planeRoboports(productivity, field, recipeTree, score)
        var attempts = 0
        var same = 0
        val startTime = System.currentTimeMillis()
        try {
            while (true) {
                val toRemove = listToRemove(best, field, removeBeacons = false).toMutableList()
                val toRemoveSet = getPairsToRemove(toRemove, attempts)
                logger.info { "Upgrade roboports two attempt: $attempts" }
                val scoreRoboport = roboportsManager.planeRoboports(best, field, recipeTree, score)
                if (scoreRoboport < bestScore) {
                    same = 0
                    bestScore = scoreRoboport
                    logger.info { "New Upgrade roboports two best score: $bestScore" }
                } else if (same > toRemove.size) {
                    return best
                } else {
                    same++
                }
                val time = System.currentTimeMillis()
                if (time - startTime > roboport_timeout) {
                    logger.info { "New Upgrade roboports two timeout" }
                    return best
                }
                attempts++
                var localBestScore = bestScore
                while (toRemoveSet.isNotEmpty()) {
                    val (building, building2) = toRemoveSet.removeAt(0)
                    if (building.type == BuildingType.SMELTER && building2.type == BuildingType.SMELTER) continue //todo: minor: fix for smelters
                    if (building.type == BuildingType.ASSEMBLER && building2.type == BuildingType.ASSEMBLER &&
                        (building as Assembler).recipe == (building2 as Assembler).recipe
                    ) continue

                    logger.info { "Upgrade roboports two: $building, $building2" }

                    val removed1 = removeWithConnectors(best, building)
                    val removed = removeWithConnectors(removed1, building2)

                    val recipe1 = if (building2 is Assembler) building2.recipe else (building2 as Smelter).recipe
                    val newBuilding1 = Utils.getBuilding(
                        building.place.start,
                        building2.type,
                        recipe = recipe1
                    )
                    val add1 = removed.addBuilding(
                        newBuilding1
                    )
                    val recipe2 = if (building is Assembler) building.recipe else (building as Smelter).recipe
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
                    var updateByProductivity = false
                    if (recipe1 == productionScore.second || recipe2 == productionScore.second) {
                        val newProductivity = scoreManager.calculateScore(add2, recipeTree, false)
                        if (newProductivity.first > productionScore.first) {
                            updateByProductivity = true
                            productionScore = newProductivity
                            logger.info { "Upgrade roboports two update productivity ${productionScore.second}: ${productionScore.first}" }
                        }
                    }


                    globalPlanner.planeChests(add2, field, newBuilding1, recipe1).forEach { chest1 ->
                        globalPlanner.planeChests(chest1, field, newBuilding2, recipe2).forEach { current ->
                            val newScore = roboportsManager.planeRoboports(current, field, recipeTree, score)
                            if (newScore < localBestScore || updateByProductivity) {
                                updateByProductivity = false
                                logger.info { "Upgrade roboports two update best $newScore" }
                                logger.info { Utils.convertToJson(current) }
                                localBestScore = newScore
                                best = current
                            }
                        }
                    }
                }
            }
        } catch (e: InterruptedException) {
            logger.info { "Interrupted" }
            return best
        }
    }

    fun upgradeRobots(start: State, recipeTree: Map<String, ProcessedItem>, field: Field, score: Double): State {
        var best = start
        var bestScore = roboportsManager.planeRoboports(start, field, recipeTree, score)
        var attempts = 0
        var same = 0
        while (true) {
            val toRemove = listToRemove(best, field, removeBeacons = false, includeScip = true).toMutableList()
            logger.info { "Upgrade roboports attempt: $attempts" }
            val scoreRoboport = roboportsManager.planeRoboports(best, field, recipeTree, score)
            if (scoreRoboport < bestScore) {
                same = 0
                bestScore = scoreRoboport
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
                val recipe =
                    when (building) {
                        is Assembler -> building.recipe
                        is Smelter -> building.recipe
                        else -> throw IllegalStateException()
                    }

                globalPlanner.planeChests(add, field, building, recipe).forEach { current ->
                    val newScore = roboportsManager.planeRoboports(current, field, recipeTree, score)
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

    //0.2 0.3 0.5
    fun downgradeSpeed(upgrade: State, recipeTree: Map<String, ProcessedItem>, fieldPrep: Field): State {
        val score = scoreManager.calculateScore(upgrade, recipeTree, false)
        var downgraded = upgrade
        //calculate productivity and extras in terms of speed
        extraProductivity(downgraded, recipeTree, log = true)

        //map beacons and assemblers
        val beaconsToAssembler = mapBeaconsToAssemblers(downgraded)
        //downgrade productivity - first beacons, then assemblers
        beaconsToAssembler.filter {
            it.value.isEmpty()
        }.forEach { (beacon, _) ->
            logger.info { "Unused beacon: $beacon" }
            downgraded = downgraded.removeBuilding(beacon)
        }

        beaconsToAssembler.filter {
            it.value.size == 1
        }.forEach {
            logger.info { "Single beacon: ${it.key} -> ${it.value[0]}" }
        }

        var state = State(emptySet(), emptyMap(), downgraded.size)
        beaconsToAssembler.filter { it.value.size == 1 }.keys.forEach {
            state = state.addBuilding(it)!!
        }
        logger.info { Utils.convertToJson(state) }

        val mapBeaconsToAssembler = mapBeaconsToAssemblers(downgraded)
            .filter { it.key.moduleLvl == 3 }
            .filter {
                it.value.filter { b -> isSpeedModule(getItem(b)) }.size <= 1
            }.filter {
                !it.value.any { b -> getItem(b).contains("#") }
            }.toMutableMap()

        //downgrade beacons with not more than 1 speed module assembler
        while (true) {
            downgraded = downgradeBeacon(downgraded, recipeTree, mapBeaconsToAssembler, score.first) ?: break
        }
        val extra1 = extraProductivity(downgraded, recipeTree, log = true)
        logger.info { "After downgrade beacons: ${Utils.convertToJson(downgraded)}" }

        TechnologyTreePlanner.productivity_module_possible_replace.forEach {
            logger.info { "Possible replace: $it ${extra1[it]}" }
        }

        //downgrade assemblers speed modules
        downgraded = downgradeAssemblers(downgraded, recipeTree, extra1, fieldPrep, 2, score.first)
        extraProductivity(downgraded, recipeTree, log = true)
        logger.info { "After downgrade assemblers: ${Utils.convertToJson(downgraded)}" }

        //downgrade beacons while possible
        val mapBeaconsToAssembler2 = mapBeaconsToAssemblers(downgraded)
            .filter { it.key.moduleLvl == 3 }
            .filter {
                !it.value.any { b -> getItem(b).contains("#") }
            }.toMutableMap()

        while (true) {
            downgraded = downgradeBeacon(downgraded, recipeTree, mapBeaconsToAssembler2, score.first) ?: break
        }
        val extra2 = extraProductivity(downgraded, recipeTree, log = true)
        logger.info { "After downgrade beacons2: ${Utils.convertToJson(downgraded)}" }

        downgraded = downgradeAssemblers(downgraded, recipeTree, extra2, fieldPrep, 1, score.first)
        extraProductivity(downgraded, recipeTree, log = true)
        logger.info { "After downgrade assemblers2: ${Utils.convertToJson(downgraded)}" }


        scoreManager.calculateScore(downgraded, recipeTree, true)

        return downgraded
    }

    private fun downgradeAssemblers(
        downgraded: State,
        recipeTree: Map<String, ProcessedItem>,
        extra: Map<String, Double>,
        fieldPrep: Field,
        to: Int,
        score: Double
    ): State {
        extra.filter {
            it.value > 0.5 && isSpeedModule(it.key)
        }.forEach {
            val buildings = scoreManager.buildingsForUnit(it.key, downgraded)
            val first = buildings.first()
            val downgrade = when (first) {
                is Assembler -> 0.4 * to
                is ChemicalPlant -> 0.3 * to
                is OilRefinery -> 0.3 * to
                is Smelter -> 0.2 * to
                else -> throw IllegalStateException("Wrong type")
            }
            val number = (it.value / downgrade).toInt()
            logger.info { "Downgrade assembler: ${it.key} ${it.value} ${buildings.size} $number" }
            val toDowngrade = buildings.subList(0, min(number, buildings.size))
            toDowngrade.forEach { b ->
                val currentModule = when (b) {
                    is Assembler -> b.moduleLvl
                    is ChemicalPlant -> b.moduleLvl
                    is OilRefinery -> b.moduleLvl
                    is Smelter -> b.moduleLvl
                    else -> throw IllegalStateException("Wrong type")
                }
                if (currentModule == to + 1) {
                    when (b) {
                        is Assembler -> b.moduleLvl = to
                        is ChemicalPlant -> b.moduleLvl = to
                        is OilRefinery -> b.moduleLvl = to
                        is Smelter -> b.moduleLvl = to
                        else -> throw IllegalStateException("Wrong type")
                    }
                    if (scoreManager.calculateScore(downgraded, recipeTree, false).first < score - 0.0001) {
                        logger.info { "Scip assembler: $b" }
                        when (b) {
                            is Assembler -> b.moduleLvl = to + 1
                            is ChemicalPlant -> b.moduleLvl = to + 1
                            is OilRefinery -> b.moduleLvl = to + 1
                            is Smelter -> b.moduleLvl = to + 1
                            else -> throw IllegalStateException("Wrong type")
                        }
                    }
                }
            }
        }

        return downgraded
    }

    private fun mapBeaconsToAssemblers(upgrade: State): Map<Beacon, List<Building>> {
        val beaconsToAssembler = upgrade.buildings.filterIsInstance<Beacon>().associateWith { beacon ->
            upgrade.buildings.filter {
                it.type == BuildingType.ASSEMBLER ||
                        it.type == BuildingType.SMELTER ||
                        it.type == BuildingType.CHEMICAL_PLANT ||
                        it.type == BuildingType.LAB ||
                        it.type == BuildingType.ROCKET_SILO ||
                        it.type == BuildingType.OIL_REFINERY
            }.filter { a -> Utils.isBeaconAffect(beacon, a) }
        }
        return beaconsToAssembler
    }

    private fun downgradeBeacon(
        downgraded: State,
        recipeTree: Map<String, ProcessedItem>,
        beaconsToAssembler: MutableMap<Beacon, List<Building>>,
        first: Double
    ): State? {
        val mapExtraProductivity = extraProductivity(downgraded, recipeTree)
        beaconsToAssembler.filter {
            !it.value.any { b -> mapExtraProductivity[getItem(b)]!! < 0.5 }
        }.minByOrNull { it.value.size }?.let { (beacon, a) ->
            logger.info { "Downgrade beacon: $beacon" }
            a.forEach {
                val extra = mapExtraProductivity[getItem(it)]!!
                logger.info { "Downgrade affect: $it $extra" }
            }
            val deleted = downgraded.removeBuilding(beacon)
            val replaced = deleted.addBuilding(
                Utils.getBuilding(
                    beacon.place.start,
                    BuildingType.BEACON,
                    moduleLvl = 2
                )
            )
            beaconsToAssembler.remove(beacon)
            if (scoreManager.calculateScore(replaced!!, recipeTree, false).first < first - 0.0001) {
                logger.info { "Scip beacon: $beacon" }
                return downgraded
            }

            return replaced
        }
        return null
    }

    private fun extraProductivity(
        downgraded: State,
        recipeTree: Map<String, ProcessedItem>,
        log: Boolean = false
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val scoreMap = scoreManager.scoreMap(downgraded, recipeTree, false)
        val min = scoreMap.minBy { it.first }.first
        scoreMap.map {
            val recipe = recipeTree[it.second]!!
            val extraProduce = it.first - min
            val extraProductivity = extraProduce * recipe.productivity()
            result[it.second] = extraProductivity
            Pair(it.second, extraProductivity)
        }

        val (oil, heavyOil, lightOil) = OilPlanner().calculateOil(recipeTree, 1.0)
        val oilScore =
            (scoreManager.calculateScoreForItemWithProductivity("crude-oil", downgraded, oil / 100.0 * 5.0) - min) *
                    (oil / 100.0 * 5.0)
        result["crude-oil"] = oilScore
        val heavyOilScore =
            (scoreManager.calculateScoreForItemWithProductivity("heavy-oil", downgraded, heavyOil / 40 * 2) - min) *
                    (heavyOil / 40 * 2)
        result["heavy-oil"] = heavyOilScore
        val lightOilScore =
            (scoreManager.calculateScoreForItemWithProductivity("light-oil", downgraded, lightOil / 30 * 2) - min) *
                    (lightOil / 30 * 2)
        result["light-oil"] = lightOilScore
        if (log) {
            result.asSequence().sortedBy { it.value }.forEach {
                logger.info { "Downgrade productivity: ${it.key} ${it.value} ${isSpeedModule(it.key)}" }
            }
        }
        return result
    }

    private fun getItem(b: Building): String {
        return when (b.type) {
            BuildingType.ASSEMBLER -> (b as Assembler).recipe
            BuildingType.OIL_REFINERY -> "crude-oil"
            BuildingType.LAB -> "science-approximation"
            BuildingType.ROCKET_SILO -> "space-science-pack"
            BuildingType.SMELTER -> (b as Smelter).recipe
            BuildingType.CHEMICAL_PLANT -> {
                when ((b as ChemicalPlant).recipe) {
                    "solid-fuel-from-light-oil" -> "solid-fuel"
                    "light-oil-cracking" -> "light-oil"
                    "heavy-oil-cracking" -> "heavy-oil"
                    else -> (b as ChemicalPlant).recipe
                }
            }

            else -> throw IllegalStateException("Wrong type")
        }
    }

    private fun isSpeedModule(item: String) = when (item) {
        in TechnologyTreePlanner.productivity_module_limitation -> false
        in TechnologyTreePlanner.productivity_module_limitation_lvl1 -> false
        "science-approximation" -> false
        else -> true
    }

}