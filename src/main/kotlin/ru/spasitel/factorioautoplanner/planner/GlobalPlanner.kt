package ru.spasitel.factorioautoplanner.planner

import com.google.gson.Gson
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.*
import ru.spasitel.factorioautoplanner.data.auto.BlueprintDTO
import ru.spasitel.factorioautoplanner.data.building.*
import ru.spasitel.factorioautoplanner.formatter.BluePrintFieldExtractor
import ru.spasitel.factorioautoplanner.formatter.Formatter
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

class GlobalPlanner {
    private val logger = KotlinLogging.logger {}
    private val scoreManager = ScoreManager()

    fun planeGlobal(recipeTree: Map<String, ProcessedItem>, fieldBlueprint: String) {
        val decodeField = Formatter.decode(fieldBlueprint)
        val dto = Gson().fromJson(decodeField, BlueprintDTO::class.java)
        val field = BluePrintFieldExtractor().transformBlueprintToField(dto)

        printPredeployed(field, recipeTree)

        Utils.checkLiquids = true

        var double = false
        var min = 3.5
        var max = 4.5
        var best: State? = null
        while (max - min > 0.05) {
            val mid = (max + min) / 2
            logger.info { "========= planeGlobal $min $max : $mid =========" }
            val greedy = planeGreedy(recipeTree, field, mid)
            if (greedy != null) {
                logger.info { "Greedy result: " + Utils.convertToJson(greedy) }
            } else {
                logger.info { "No greedy solution found" }
            }

            val withRoboports = greedy?.let { planeRoboports(it, field, recipeTree) }

            if (withRoboports != null) {
                min = scoreManager.calculateScore(withRoboports, recipeTree)
                best = withRoboports
                if (double) {
                    max = min * 3
                }
            } else {
                double = false
                max = mid
            }
            logger.info { "========= planeGlobal next $min $max =========" }
        }

        if (best == null) {
            logger.error { "No solution found" }
            return
        }
        val upgrade = planeUpgrade(recipeTree, field, best)

        planeDowngrade(upgrade)

    }

    private fun printPredeployed(field: Field, recipeTree: Map<String, ProcessedItem>) {
        val done = setOf(
            "battery",
            "plastic-bar",
            "space-science-pack",
            "science-approximation",
            "sulfuric-acid",
            "sulfur",
            "lubricant",
            "rocket-fuel",
            "solid-fuel",
        )
        done.forEach {
            val score = scoreManager.calculateScoreForItem(field.state, it, recipeTree)
            logger.info { "Planned $it: $score" }
        }
        val (oil, heavyOil, lightOil) = OilPlanner().calculateOil(recipeTree, 1.0)
        val oilScore = scoreManager.calculateScoreForItemWithProductivity("crude-oil", field.state, oil / 100.0 * 5.0)
        val heavyOilScore =
            scoreManager.calculateScoreForItemWithProductivity("heavy-oil", field.state, heavyOil / 40 * 2)
        val lightOilScore =
            scoreManager.calculateScoreForItemWithProductivity("light-oil", field.state, lightOil / 30 * 2)
        logger.info { "Planned oil: $oilScore, heavy: $heavyOilScore, light: $lightOilScore" }
    }


    private fun planeGreedy(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        val planed = setOf(
            "processing-unit",
            "electronic-circuit",
            "battery",
            "plastic-bar",
            "electric-engine-unit",
            "space-science-pack",
            "science-approximation",
            "sulfuric-acid",
            "sulfur",
            "lubricant",
            "rocket-fuel",
            "solid-fuel",
        )
        var state = planeSpecial(recipeTree, field, mid) ?: return null


        val done = planed.plus(TechnologyTreePlanner.base).toMutableSet()
        while (recipeTree.keys.minus(done).isNotEmpty()) {
            val item = calculateNextItem(recipeTree, done)
            state = planeGreedyItem(recipeTree, item, state, field, mid) ?: return null
            done.add(item)
        }
        return state
    }

    private fun planeSpecial(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        val processors = planeUnit(
            field.state,
            field,
            recipeTree,
            mid,
            "processing-unit",
            ::stepProcessingUnit
        ) ?: return null
        val circuits =
            planeUnit(processors, field, recipeTree, mid, "electronic-circuit", ::stepCircuits) ?: return null
        val batteries = planeUnit(circuits, field, recipeTree, mid, "battery", ::stepBattery) ?: return null
        val engines =
            planeUnit(batteries, field, recipeTree, mid, "electric-engine-unit", ::stepElectricEngine) ?: return null
        return engines
    }


    private fun planeUnit(
        state: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        mid: Double,
        unit: String,
        stepUnit: (
            start: Cell,
            current: State,
            field: Field,
            recipeTree: Map<String, ProcessedItem>,
            unit: String
        ) -> Pair<State?, Double?>
    ): State? {
        var score = scoreManager.calculateScoreForItem(state, unit, recipeTree)
        logger.info { "Start $unit, score: $score" }
        var current = state
        val scip: MutableSet<Cell> = mutableSetOf()
        while (score < mid) {
            var next: State? = null
            var nextScore: Double? = null
            if (score > 0) {
                next = planeBeacon(current, field, unit, recipeTree)
                if (next != null && (unit == "processing-unit" || unit == "electronic-circuit")) {
                    next = planeBeacon(next, field, unit, recipeTree)
                }
                if (next != null && unit == "processing-unit") {
                    next = planeBeacon(next, field, unit, recipeTree)
                }
                nextScore = next?.let { scoreManager.calculateScoreForItem(it, unit, recipeTree) }
            }
            val starts = placesForItem(current, field, unit, recipeTree)
            for (start in starts) {
                if (scip.contains(start)) {
                    continue
                }
                // sort by score
                logger.info { "Trying $unit from $start" }
                val pair = stepUnit(start, current, field, recipeTree, unit)
                if (pair.first == null) {
                    scip.add(start)
                    continue
                }
                if (next == null || pair.second!! > nextScore!!) {
                    next = pair.first
                    nextScore = pair.second
                }
                break
            }

            if (next == null) {
                logger.info { "No solution found for $unit" }
                return null
            }
            val added =
                next.buildings.filter { it.type == BuildingType.BEACON || it.type == BuildingType.ASSEMBLER || it.type == BuildingType.SMELTER }
                    .minus(current.buildings)
            current = next
            score = nextScore!!
            logger.info { "Step $unit, score: $score:, added: $added" }
            logger.trace { Utils.convertToJson(current) }
        }
        return current
    }

    private fun stepElectricEngine(
        start: Cell,
        current: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        unit: String
    ): Pair<State?, Double?> {
        val procWithConnections =
            TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, unit) }
        for (direction in Direction.entries) {
            val procBuilding = Utils.getBuilding(
                start,
                BuildingType.ASSEMBLER,
                direction = direction.direction,
                recipe = unit
            )
            val proc = current.addBuilding(procBuilding) ?: continue
            val planeLiquid = planeLiquid(proc, field, procBuilding, "lubricant") ?: continue
            // add chest and inserters
            val planeChest = planeChests(planeLiquid, field, procBuilding, unit)
            planeChest.map { it to procBuilding }.forEach { procWithConnections.add(it) }
        }
        if (procWithConnections.isNotEmpty()) {
            val bestWire = procWithConnections.first()
            val bestWireState = bestWire.first
            val bestWireScore = scoreManager.calculateScoreForItem(bestWireState, unit, recipeTree)
            return Pair(bestWireState, bestWireScore)
        }
        return Pair(null, null)
    }

    private fun stepBattery(
        start: Cell,
        current: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        unit: String
    ): Pair<State?, Double?> {
        val procWithConnections =
            TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, unit) }
        for (direction in Direction.entries) {
            val procBuilding = Utils.getBuilding(
                start,
                BuildingType.CHEMICAL_PLANT,
                direction = direction.direction,
                recipe = unit
            )
            val proc = current.addBuilding(procBuilding) ?: continue
            val planeLiquid = planeLiquid(proc, field, procBuilding, "sulfuric-acid") ?: continue
            // add chest and inserters
            val planeChest = planeChests(planeLiquid, field, procBuilding, unit)
            planeChest.map { it to procBuilding }.forEach { procWithConnections.add(it) }
        }
        if (procWithConnections.isNotEmpty()) {
            val bestWire = procWithConnections.first()
            val bestWireState = bestWire.first
            val bestWireScore = scoreManager.calculateScoreForItem(bestWireState, unit, recipeTree)
            return Pair(bestWireState, bestWireScore)
        }
        return Pair(null, null)
    }

    private fun stepProcessingUnit(
        start: Cell,
        current: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        unit: String
    ): Pair<State?, Double?> {
        val procWithConnections =
            TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, "processing-unit") }
        for (direction in Direction.entries) {
            val procBuilding = Utils.getBuilding(
                start,
                BuildingType.ASSEMBLER,
                direction = direction.direction,
                recipe = "processing-unit"
            )
            val proc = current.addBuilding(procBuilding) ?: continue
            val planeLiquid = planeLiquid(proc, field, procBuilding, "sulfuric-acid") ?: continue
            // add chest and inserters
            val planeChest = planeChests(planeLiquid, field, procBuilding, "processing-unit")
            planeChest.map { it to procBuilding }.forEach { procWithConnections.add(it) }
        }
        //add green circuits, sort by score
        for (blue in procWithConnections) {
            val greenWithConnections =
                TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, "electronic-circuit#blue") }
            val greenPositions = placeConnected(blue.first, blue.second, field, "electronic-circuit#blue")
            for (greenPosition in greenPositions) {
                val greenBuilding = Utils.getBuilding(
                    greenPosition,
                    BuildingType.ASSEMBLER,
                    recipe = "electronic-circuit#blue"
                )
                val green = blue.first.addBuilding(greenBuilding) ?: continue
                val planeConnection = planeConnection(green, field, greenBuilding, blue.second) ?: continue
                // add chest and inserters
                val planeChest =
                    planeChests(planeConnection, field, greenBuilding, "electronic-circuit#blue", false)
                planeChest.map { g -> g to greenBuilding }.forEach { g -> greenWithConnections.add(g) }
            }
            //add copper wire

            for (green in greenWithConnections) {
                val wireWithConnections =
                    TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, "copper-cable#blue") }
                val wirePositions = placeConnected(green.first, green.second, field, "copper-cable#blue")
                for (wirePosition in wirePositions) {
                    val wireBuilding = Utils.getBuilding(
                        wirePosition,
                        BuildingType.ASSEMBLER,
                        recipe = "copper-cable#blue"
                    )
                    val wire = green.first.addBuilding(wireBuilding) ?: continue
                    val planeConnection = planeConnection(wire, field, wireBuilding, green.second) ?: continue
                    // add chest and inserters
                    val planeChest =
                        planeChests(planeConnection, field, wireBuilding, "copper-cable#blue", false)
                    planeChest.map { g -> g to wireBuilding }.forEach { g -> wireWithConnections.add(g) }
                }

                if (wireWithConnections.isNotEmpty()) {
                    val bestWire = wireWithConnections.first()
                    val bestWireState = bestWire.first
                    val bestWireScore = scoreManager.calculateScoreForItem(bestWireState, "processing-unit", recipeTree)
                    return Pair(bestWireState, bestWireScore)
                }
            }
        }
        return Pair(null, null)
    }

    private fun stepCircuits(
        start: Cell,
        current: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        unit: String
    ): Pair<State?, Double?> {
        val greenWithConnections =
            TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, "electronic-circuit") }
        val greenBuilding = Utils.getBuilding(
            start,
            BuildingType.ASSEMBLER,
            recipe = "electronic-circuit"
        )
        val green = current.addBuilding(greenBuilding) ?: return Pair(null, null)
        // add chest and inserters
        val planeChest = planeChests(green, field, greenBuilding, "electronic-circuit")
        planeChest.map { g -> g to greenBuilding }.forEach { g -> greenWithConnections.add(g) }
        //add copper wire

        for (greenWithConnection in greenWithConnections) {
            val wireWithConnections =
                TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, "copper-cable#green") }
            val wirePositions = placeConnected(
                greenWithConnection.first,
                greenWithConnection.second,
                field,
                "copper-cable#green"
            )
            for (wirePosition in wirePositions) {
                val wireBuilding = Utils.getBuilding(
                    wirePosition,
                    BuildingType.ASSEMBLER,
                    recipe = "copper-cable#green"
                )
                val wire = greenWithConnection.first.addBuilding(wireBuilding) ?: continue
                val planeConnection =
                    planeConnection(wire, field, wireBuilding, greenWithConnection.second) ?: continue
                // add chest and inserters
                val planeChestWire =
                    planeChests(planeConnection, field, wireBuilding, "copper-cable#green", false)
                planeChestWire.map { g -> g to wireBuilding }.forEach { g -> wireWithConnections.add(g) }
            }

            if (wireWithConnections.isNotEmpty()) {
                val bestWire = wireWithConnections.first()
                val bestWireState = bestWire.first
                val bestWireScore = scoreManager.calculateScoreForItem(bestWireState, "electronic-circuit", recipeTree)
                return Pair(bestWireState, bestWireScore)
            }
        }
        return Pair(null, null)
    }


    private fun planeChests(
        planeLiquid: State,
        field: Field,
        building: Building,
        s: String,
        withOutput: Boolean = true
    ): List<State> {
        val withInputChest =
            GreedyPlanner().addChests(planeLiquid, building, BuildingType.REQUEST_CHEST, field = field)
        if (withOutput) {
            val result = mutableListOf<State>()
            for (withInputChestState in withInputChest) {
                val withOutputChest =
                    GreedyPlanner().addChests(
                        withInputChestState,
                        building,
                        BuildingType.PROVIDER_CHEST,
                        items = setOf(s),
                        field = field
                    )
                result.addAll(withOutputChest)
            }
            return result
        } else {
            return withInputChest.toList()
        }
    }


    private fun planeConnection(state: State, field: Field, from: Building, to: Building): State? {
        val all = getOutInserters(from)
        val toBuilding = all.filter { to.place.cells.contains(it.to()) }
        val empty = toBuilding.filter { !state.map.containsKey(it.place.start) }
        empty.forEach {
            val newState = state.addBuilding(it)
            if (newState != null) {
                return newState
            }
        }
        return null
    }

    private fun getOutInserters(from: Building): Set<Inserter> {
        val result = mutableSetOf<Inserter>()
        for (x in 0..2) {
            result.add(
                Utils.getBuilding(
                    from.place.start.up().move(Direction.RIGHT, x),
                    BuildingType.INSERTER,
                    direction = Direction.DOWN.direction
                ) as Inserter
            )
            result.add(
                Utils.getBuilding(
                    from.place.start.move(Direction.DOWN, 3).move(Direction.RIGHT, x),
                    BuildingType.INSERTER,
                    direction = Direction.UP.direction
                ) as Inserter
            )
            result.add(
                Utils.getBuilding(
                    from.place.start.left().move(Direction.DOWN, x),
                    BuildingType.INSERTER,
                    direction = Direction.RIGHT.direction
                ) as Inserter
            )
            result.add(
                Utils.getBuilding(
                    from.place.start.move(Direction.RIGHT, 3).move(Direction.DOWN, x),
                    BuildingType.INSERTER,
                    direction = Direction.LEFT.direction
                ) as Inserter
            )
        }
        return result

    }

    private fun placeConnected(state: State, building: Building, field: Field, unit: String): List<Cell> {
        val result = mutableListOf<Cell>()
        for (x in -2..2) {
            result.add(Cell(building.place.start.x + x, building.place.start.y - 4))
            result.add(Cell(building.place.start.x + x, building.place.start.y + 4))
            result.add(Cell(building.place.start.x - 4, building.place.start.y + x))
            result.add(Cell(building.place.start.x + 4, building.place.start.y + x))

        }
        return result
    }


    private fun placesForItem(
        current: State,
        field: Field,
        unit: String,
        recipeTree: Map<String, ProcessedItem>
    ): List<Cell> {
        val recipe = recipeTree[unit]!!
        var freeCells = current.freeCells.filter { Utils.isBetween(it, field.assemblersField) }
        if (unit == "electric-engine-unit") {
            val building =
                current.buildings.filter { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == "lubricant" }
                    .first()
            return freeCells.sortedBy { it.maxDistanceTo(building.place.start) }
        }

        val ingredients = recipe.ingredients
        val chestsToValue = mutableMapOf<Cell, Double>()
        ingredients.forEach { (t, u) ->
            val chests = current.buildings.filter {
                it.type == BuildingType.PROVIDER_CHEST && (it as ProviderChest).items.contains(t)
            }.map { it.place.start }
            if (chests.isEmpty()) {
                return@forEach
            }
            val average = Cell(chests.map { it.x }.average().roundToInt(), chests.map { it.y }.average().roundToInt())
            chestsToValue[average] = u
        }
        if (unit == "processing-unit") {
            freeCells = freeCells.filter { it.y < 50 }//todo processing-unit only in top
        }
        val sortedByDistance = freeCells.sortedBy {
            chestsToValue.map { (t, u) ->
                getDistance(it, t) * u
            }.sum()
        }
//            .sortedBy {
//            if (Utils.isBetween(it, field.roboportsField)) 1 else 0
//        }
            .take(400)
        val sortedByPerformance = sortedByDistance.sortedBy {
            -current.performanceMap.getOrDefault(it, 0)
        }
//            .sortedBy {
//            if (Utils.isBetween(it, field.roboportsField)) 1 else 0
//        }

        return sortedByPerformance
    }

    private fun planeLiquid(current: State, field: Field, building: Building, liquid: String): State? {
        if (building.type == BuildingType.ASSEMBLER) {
            val start = building.place.start.down().right().move((building as Assembler).direction!!)
            return OilPlanner().planeLiquid(liquid, start, current, field)
        } else {
            //todo direction of liquid exit - not used
            val direction = Direction.fromInt((building as ChemicalPlant).direction)
            val start = building.place.start.down().right().move(direction).move(direction.turnRight())
            OilPlanner().planeLiquid(liquid, start, current, field)?.let { return it }
            val start2 = building.place.start.down().right().move(direction).move(direction.turnLeft())
            return OilPlanner().planeLiquid(liquid, start2, current, field)
        }
    }

    private fun getDistance(from: Cell, to: Cell): Double {
        return sqrt((from.x - to.x) * (from.x - to.x) + (from.y - to.y) * (from.y - to.y).toDouble())
    }

    private fun compareForUnit(a: Pair<State, Building>, b: Pair<State, Building>, unit: String): Int {
        val aScore = scoreManager.calculateScoreForBuilding(a, unit)
        val bScore = scoreManager.calculateScoreForBuilding(b, unit)
        if (aScore != bScore) {
            return bScore.compareTo(aScore)
        }
        val aSize = a.first.freeCells.size
        val bSize = b.first.freeCells.size
        if (aSize != bSize) {
            return bSize.compareTo(aSize)
        }

        return a.hashCode().compareTo(b.hashCode())
    }


    private fun planeBeacon(
        current: State,
        field: Field,
        unit: String,
        recipeTree: Map<String, ProcessedItem>
    ): State? {
        var score = 0.0
        var best: State? = null

        val buildingsForUnit = when (unit) {
            //blue and green - calculate which in chain is worse
            "processing-unit", "electronic-circuit" -> findLeastPerformed(current, unit)
            else -> scoreManager.buildingsForUnit(unit, current)
        }

        buildingsForUnit.forEach { assembler ->
            current.freeCells.filter { it.maxDistanceTo(assembler.place.start) < 6 }.forEach { beaconStart ->
                val beacon = Utils.getBuilding(beaconStart, BuildingType.BEACON)
                val newState = current.addBuilding(beacon)
                if (newState != null) {
                    val newScore =
                        if (unit == "processing-unit" || unit == "electronic-circuit")
                            scoreManager.calculateScoreForItem(newState, unit, recipeTree) else
                            scoreManager.calculateScoreForBuilding(Pair(newState, beacon), unit)
                    if (best == null || newScore > score
                        || (newScore == score && newState.freeCells.size > best!!.freeCells.size)
                    ) {
                        score = newScore
                        best = newState
                    }
                }
            }
        }
        return best
    }

    private fun findLeastPerformed(current: State, unit: String): List<Building> {
        val result = mutableListOf<Building>()
        val buildings =
            current.buildings.filter { it.type == BuildingType.ASSEMBLER && (it as Assembler).recipe == unit }
        buildings.forEach {
            val score = scoreManager.calculateScoreForMultiBuildings(current, it, unit)
            result.add(score.second)
        }

        return result
    }

    private fun calculateNextItem(recipeTree: Map<String, ProcessedItem>, done: Set<String>): String {
        val todo = recipeTree.filter { it.key !in done }
        val canDo = todo.filter { it.value.ingredients.keys.all { i -> i in done } }
        return canDo.toList().maxBy { it.second.ingredients.maxOf { i -> i.value } }.first
    }

    private fun planeGreedyItem(
        recipeTree: Map<String, ProcessedItem>,
        item: String,
        state: State,
        field: Field,
        mid: Double
    ): State? {
        return planeUnit(state, field, recipeTree, mid, item, ::stepUnit)
    }

    private fun stepUnit(
        start: Cell,
        current: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        unit: String
    ): Pair<State?, Double?> {
        val procWithConnections =
            TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, unit) }
        val type = if (unit == "stone-brick") BuildingType.SMELTER else BuildingType.ASSEMBLER
        val procBuilding = Utils.getBuilding(
            start,
            type,
            recipe = unit
        )
        val proc = current.addBuilding(procBuilding) ?: return Pair(null, null)
        // add chest and inserters
        val planeChest = planeChests(proc, field, procBuilding, unit)
        planeChest.map { it to procBuilding }.forEach { procWithConnections.add(it) }
        if (procWithConnections.isNotEmpty()) {
            val bestWire = procWithConnections.first()
            val bestWireState = bestWire.first
            val bestWireScore = scoreManager.calculateScoreForItem(bestWireState, unit, recipeTree)
            return Pair(bestWireState, bestWireScore)
        }
        return Pair(null, null)
    }

    private fun planeRoboports(state: State, field: Field, recipeTree: Map<String, ProcessedItem>): State? {
        // calculate provider chests with amount of items
        val providers = calculateProviderChests(state, field, recipeTree)
        // calculate request chests with amount of items
        val requests = calculateRequestChests(state, field, recipeTree)
        // calculate number of roboports
        val roboports = calculateRoboports(providers, requests)

        logger.info { "Roboports: $roboports" }
        // add roboports

        return state
    }

    private fun calculateRoboports(
        providers: Map<String, Set<Pair<ProviderChest, Double>>>,
        requests: Map<String, Set<Pair<RequestChest, Double>>>
    ): Int {
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
        return (result.map { it.value }.sum() / 220.0).toInt() + 1
    }

    private fun calculateRequestChests(
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
                val item = when (source) {
                    is Assembler -> source.recipe
                    is ChemicalPlant -> source.recipe
                    is Smelter -> "stone-brick"
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
                    if (ingredient.key == "electronic-circuit" && item == "processing-unit") {
                        continue
                    }
                    if (ingredient.key == "copper-cable" && item.split("#")[0] == "electronic-circuit") {
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

    private fun calculateProviderChests(
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
                    is Smelter -> "stone-brick"
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


    private fun planeUpgrade(recipeTree: Map<String, ProcessedItem>, field: Field, best: State): State {
        TODO("Not yet implemented") //Remove 1 or 2 buildings and replace with better
    }

    private fun planeDowngrade(upgrade: State) {
        TODO("Not yet implemented")
    }

    companion object {
        private val log = KotlinLogging.logger {}

        @JvmStatic
        fun main(args: Array<String>) {
            log.info { "Start" }
            val tree = TechnologyTreePlanner.scienceRoundTree()
            GlobalPlanner().planeGlobal(tree, BluePrintFieldExtractor.FIELD)
        }
    }
}