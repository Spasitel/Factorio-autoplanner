package ru.spasitel.factorioautoplanner.planner

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.*
import ru.spasitel.factorioautoplanner.data.building.*
import ru.spasitel.factorioautoplanner.formatter.BluePrintFieldExtractor
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

class GlobalPlanner {
    private val logger = KotlinLogging.logger {}
    private val scoreManager = ScoreManager()
    private val upgradeManager = GlobalUpgradeManager(this)

    fun planeGlobal(recipeTree: Map<String, ProcessedItem>, fieldPrep: Field, fieldFull: Field) {
        if (!isSmelter) {
            printPredeployed(fieldFull, recipeTree)
        }

        Utils.checkLiquids = true

        var double = false
        var min = 10.0
        var max = 11.5
        var best: State? = null
        val delta = if (isSmelter) 1.0 else 0.05

        while (max - min > delta) {
            val mid = (max + min) / 2
            logger.info { "========= planeGlobal $min $max : $mid =========" }
            val greedy = planeGreedy(recipeTree, fieldFull, mid)
            if (greedy != null) {
                logger.info { "Greedy result: " + Utils.convertToJson(greedy) }
            } else {
                logger.info { "No greedy solution found" }
            }

//            val withRoboports = greedy?.let { planeRoboports(it, field, recipeTree) }
            val withRoboports = greedy
            if (withRoboports != null) {
                min = scoreManager.calculateScore(withRoboports, recipeTree, true).first
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
        val upgrade = planeUpgrade(recipeTree, fieldPrep, best)

        val downgrade = if (isSmelter) upgrade else
            planeDowngrade(upgrade, fieldPrep, recipeTree)
        val result = if (isSmelter) planeSmelterChests(downgrade, fieldPrep) else
            planeFinalChestsAndInserters(downgrade, fieldPrep, recipeTree)
        logger.info { "========= planeGlobal result =========" }
        logger.info { Utils.convertToJson(result) }
        if (!isSmelter) {
            logger.info { "Roboport score:" + RoboportsManager().planeRoboports(result, fieldFull, recipeTree) }
        }
    }

    private fun planeSmelterChests(downgrade: State, fieldPrep: Field): State {
        downgrade.buildings.filterIsInstance<RequestChest>().filter { fieldPrep.state.map[it.place.start] == null }
            .forEach {
                it.items["$smelterOre-ore"] = 300
            }
        return downgrade
    }

    private fun planeFinalChestsAndInserters(
        state: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>
    ): State {
        enumerateBuildings(state)
        // set restricts for chests
        // calculate provider chests with amount of items
        val roboportsManager = RoboportsManager()
        // calculate request chests with amount of items
        val requests = roboportsManager.calculateRequestChests(state, field, recipeTree)
        requests.forEach { (item, pairs) ->
            pairs.forEach { (chest, amount) ->
                chest.items[item] = (amount * 60).toInt() + 1
            }
        }
        state.buildings.filterIsInstance<Inserter>().filter { state.map[it.from()] is RequestChest }
            .filter { it.kind == "stack-inserter" }.forEach {
                val amount = (state.map[it.from()] as RequestChest).items.map { c -> c.value }.sum()
                //downgrade inserters
                if (amount < 100) {
                    it.kind = "inserter"
                } else if (amount < 300) {
                    it.kind = "fast-inserter"
                }
            }

        val providers = roboportsManager.calculateProviderChests(state, field, recipeTree)
        providers.forEach { (item, pairs) ->
            pairs.forEach { (chest, a) ->
                val amount = (a * 60).toInt() + 1
                val buildings = scoreManager.buildingsForUnit(item, state)
                val inserters = state.buildings.filterIsInstance<Inserter>().filter { it.to() == chest.place.start }
                    .filter { state.map[it.from()]!! in buildings }
                inserters.forEach { it.condition = Triple(chest.id, item, amount) }
                inserters.forEach { chest.connections.add(it.id) }
                inserters.forEach { inserter ->
                    if (inserter.kind == "stack-inserter") {
                        //downgrade inserters
                        if (amount.div(inserters.size) < 150) {
                            inserter.kind = "inserter"
                        } else if (amount.div(inserters.size) < 415) {
                            inserter.kind = "fast-inserter"
                        }
                    }
                }
            }
        }

        return state
    }

    private fun enumerateBuildings(downgrade: State) {
        downgrade.buildings.forEach {
            it.enumerate()
        }
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
        if (isSmelter && smelterType == "steel") {
            return planeSteel(recipeTree, field, mid)
        }
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

        var state =
            if (isSmelter)
                field.state
            else
                planeSpecial(recipeTree, field, mid) ?: return null


        val done =
            if (isSmelter)
                if (smelterType == "steel") {
                    mutableSetOf("iron-plate")
                } else {
                    mutableSetOf("copper-ore", "iron-ore")
                }
            else
                planed.plus(TechnologyTreePlanner.base).toMutableSet()
        while (recipeTree.keys.minus(done).isNotEmpty()) {
            val item = calculateNextItem(recipeTree, done)
            state = planeGreedyItem(recipeTree, item, state, field, mid) ?: return null
            done.add(item)
        }
        return state
    }

    private fun planeSteel(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State {
        return planeUnit(field.state, field, recipeTree, mid, "steel-plate", ::stepSteel)!!
    }

    fun stepSteel(
        start: Cell,
        current: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        unit: String
    ): Pair<State?, Double?> {
        val greenWithConnections =
            TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, "steel-plate") }
        val greenBuilding = Utils.getBuilding(
            start,
            BuildingType.SMELTER,
            recipe = "steel-plate"
        )
        val green = current.addBuilding(greenBuilding) ?: return Pair(null, null)
        // add chest and inserters
        val planeChest = planeChests(green, field, greenBuilding, "steel-plate", withInput = false)
        planeChest.map { g -> g to greenBuilding }.forEach { g -> greenWithConnections.add(g) }
        //add copper wire

        for (greenWithConnection in greenWithConnections) {
            val wireWithConnections =
                TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, "iron-plate#steel") }
            val wirePositions = placeConnected(
                greenWithConnection.first,
                greenWithConnection.second,
                field,
                "iron-plate#steel"
            )
            for (wirePosition in wirePositions) {
                val wireBuilding = Utils.getBuilding(
                    wirePosition,
                    BuildingType.SMELTER
                )
                val wire = greenWithConnection.first.addBuilding(wireBuilding) ?: continue
                val planeConnection =
                    planeConnection(wire, field, wireBuilding, greenWithConnection.second) ?: continue
                // add chest and inserters
                val planeChestWire =
                    planeChests(planeConnection, field, wireBuilding, "iron-plate#steel")
                planeChestWire.map { g -> g to wireBuilding }.forEach { g -> wireWithConnections.add(g) }
            }

            if (wireWithConnections.isNotEmpty()) {
                val bestWire = wireWithConnections.first()
                val bestWireState = bestWire.first
                val bestWireScore = scoreManager.calculateScoreForItem(bestWireState, "steel-plate", recipeTree)
                return Pair(bestWireState, bestWireScore)
            }
        }
        return Pair(null, null)
    }


    fun stepAllSteel(
        start: Cell,
        current: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        unit: String
    ): SortedSet<Pair<State?, Double?>> {

        val result = TreeSet<Pair<State?, Double?>> { a, b ->
            if (a.second!! != b.second!!) a.second!!.compareTo(b.second!!) else
                a.hashCode().compareTo(b.hashCode())
        }

        val building = Utils.getBuilding(
            start,
            BuildingType.SMELTER,
            recipe = "steel-plate"
        )
        val withBuilding = current.addBuilding(building) ?: return sortedSetOf(Pair(null, null))
        // add chest and inserters
        val planeChest = planeChests(withBuilding, field, building, "steel-plate", withInput = false)
        planeChest.forEach { st ->

            val wirePositions = placeConnected(
                st,
                building,
                field,
                "iron-plate#steel"
            )
            for (wirePosition in wirePositions) {
                val wireBuilding = Utils.getBuilding(
                    wirePosition,
                    BuildingType.SMELTER
                )
                val wire = st.addBuilding(wireBuilding) ?: continue
                val planeConnection =
                    planeConnection(wire, field, wireBuilding, building) ?: continue
                // add chest and inserters
                val planeChestWire =
                    planeChests(planeConnection, field, wireBuilding, "iron-plate#steel")

                planeChestWire.forEach {
                    val bestWireScore = scoreManager.calculateScoreForItem(it, unit, recipeTree)
                    result.add(Pair(it, bestWireScore))
                }
            }

        }
        return result
    }


    private fun planeSpecial(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        val delta = 0.7
        val processors = planeUnit(
            field.state,
            field,
            recipeTree,
            mid + delta,
            "processing-unit",
            ::stepProcessingUnit,
            limit = 0
        ) ?: return null
        val circuits =
            planeUnit(processors, field, recipeTree, mid + delta, "electronic-circuit", ::stepCircuits) ?: return null
        val batteries = planeUnit(circuits, field, recipeTree, mid + delta, "battery", ::stepBattery) ?: return null
        val engines =
            planeUnit(batteries, field, recipeTree, mid + delta, "electric-engine-unit", ::stepElectricEngine)
                ?: return null
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
        ) -> Pair<State?, Double?>,
        limit: Int = 5
    ): State? {
        var score = scoreManager.calculateScoreForItem(state, unit, recipeTree)
        logger.info { "Start $unit, score: $score" }
        var current = state
        val scip: MutableSet<Cell> = mutableSetOf()
        while (score < mid) {
            val (next: State?, nextScore: Double?) = planeSingleStep(
                score,
                current,
                field,
                unit,
                recipeTree,
                scip,
                stepUnit,
                limit = limit
            )

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

    fun planeSingleStep(
        score: Double,
        current: State,
        field: Field,
        unit: String,
        recipeTree: Map<String, ProcessedItem>,
        scip: MutableSet<Cell>,
        stepUnit: (start: Cell, current: State, field: Field, recipeTree: Map<String, ProcessedItem>, unit: String) -> Pair<State?, Double?>,
        limit: Int = 10,
        special: Boolean = false
    ): Pair<State?, Double?> {
        var next: State? = null
        var nextScore: Double? = null
        if (score > 0) {
            next = planeBeacon(current, field, unit, recipeTree)
            if (next != null && (unit == "processing-unit" || unit == "electronic-circuit" || unit == "steel-plate")) {
                next = planeBeacon(next, field, unit, recipeTree)
            }
            if (next != null && unit == "processing-unit") {
                next = planeBeacon(next, field, unit, recipeTree)
            }
            nextScore = next?.let { scoreManager.calculateScoreForItem(it, unit, recipeTree) }
        }
        if (special) {
            return Pair(next, nextScore)
        }
        val starts = placesForItem(current, field, unit, recipeTree).minus(scip)
        var count = 0
        logger.info { "Trying $unit from $starts" }
        for (start in starts) {
            if (scip.contains(start)) {
                logger.info { "Skipping $start" }
                continue
            }
            // sort by score
            val pair = stepUnit(start, current, field, recipeTree, unit)
            if (pair.first == null) {
                scip.add(start)
                continue
            }
            if (next == null
                || pair.second!! > nextScore!!
                || (pair.second == nextScore && pair.first!!.freeCells.size > next.freeCells.size)
                || (pair.second == nextScore && pair.first!!.freeCells.size == next.freeCells.size && pair.first!!.emptyCountScore > next.emptyCountScore)
            ) {
                next = pair.first
                nextScore = pair.second
            }
            count++
            if (count > limit) {
                break
            }
        }
        return Pair(next, nextScore)
    }

    fun planeAllSingleStep(
        score: Double,
        current: State,
        field: Field,
        unit: String,
        recipeTree: Map<String, ProcessedItem>,
        scip: MutableSet<Cell>,
        stepUnit: (start: Cell, current: State, field: Field, recipeTree: Map<String, ProcessedItem>, unit: String) -> SortedSet<Pair<State?, Double?>>,
        limit: Int = 10,
        special: Boolean = false
    ): Set<Pair<Double, State>> {
        val result = mutableSetOf<Pair<Double, State>>()

        result.addAll(planeBeaconSet(current, field, unit, recipeTree))
        logger.info { "Beacon results count: ${result.size}" }
        if (special) {
            return result
        }
        val starts = placesForItem(current, field, unit, recipeTree)
        var count = 0
        for (start in starts) {
            if (scip.contains(start)) {
                continue
            }
            // sort by score
            logger.info { "Trying $unit from $start" }
            stepUnit(start, current, field, recipeTree, unit).forEach {
                if (it.first == null) {
                    scip.add(start)
                    return@forEach
                }
                result.add(Pair(it.second!!, it.first!!))
            }

            count++
            if (count > limit) {
                break
            }
        }
        return result
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
                    planeChests(planeConnection, field, greenBuilding, "electronic-circuit#blue")
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
                        planeChests(planeConnection, field, wireBuilding, "copper-cable#blue")
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
                    planeChests(planeConnection, field, wireBuilding, "copper-cable#green")
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


    fun planeChests(
        state: State,
        field: Field,
        building: Building,
        unit: String,
        withInput: Boolean = true
    ): List<State> {
        val withInputChest = if (withInput)
            GreedyPlanner().addChests(state, building, BuildingType.REQUEST_CHEST, field = field)
        else
            setOf(state)
        if (!unit.contains("#")) {
            val result = mutableListOf<State>()
            for (withInputChestState in withInputChest) {
                val withOutputChest =
                    GreedyPlanner().addChests(
                        withInputChestState,
                        building,
                        BuildingType.PROVIDER_CHEST,
                        items = setOf(unit),
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
        val sortedByDistance = if (unit == "processing-unit") {
            //processing-unit only in top
            freeCells.filter { it.y < 60 }.sortedBy { -it.x }.take(400)
        } else {
            freeCells.sortedBy {
                chestsToValue.map { (t, u) ->
                    getDistance(it, t) * u
                }.sum()
//            .sortedBy {
//            if (Utils.isBetween(it, field.roboportsField)) 1 else 0
//        }
            }.take(400)
        }

        val sortedByPerformance = sortedByDistance.sortedBy {
            -current.performanceMap.getOrDefault(it, 0.0)
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

        val aFreeScore = a.first.emptyCountScore
        val bFreeScore = b.first.emptyCountScore
        if (aFreeScore != bFreeScore) {
            return bFreeScore.compareTo(aFreeScore)
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
            "processing-unit", "electronic-circuit", "steel-plate" -> findLeastPerformed(current, unit)
            else -> scoreManager.buildingsForUnit(unit, current)
        }
        val cells = current.freeCells
            .filter { buildingsForUnit.any { assembler -> it.maxDistanceTo(assembler.place.start) < 6 } }
        logger.info { "Beacon starts for $unit: ${cells.size}" }
        cells.forEach { beaconStart ->
                val beacon = Utils.getBuilding(beaconStart, BuildingType.BEACON)
                val newState = current.addBuilding(beacon)
                if (newState != null) {
                    val newScore =
                        if (unit == "processing-unit" || unit == "electronic-circuit" || unit == "steel-plate")
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

        return best
    }

    private fun planeBeaconSet(
        current: State,
        field: Field,
        unit: String,
        recipeTree: Map<String, ProcessedItem>
    ): SortedSet<Pair<Double, State>> {
        val result = TreeSet<Pair<Double, State>> { a, b ->
            if (a.first != b.first) a.first.compareTo(b.first) else
                a.hashCode().compareTo(b.hashCode())
        }

        val buildingsForUnit = when (unit) {
            //blue and green - calculate which in chain is worse
            "processing-unit", "electronic-circuit", "steel-plate" -> findLeastPerformed(current, unit)
            else -> scoreManager.buildingsForUnit(unit, current)
        }

        current.freeCells
            .filter { buildingsForUnit.any { assembler -> it.maxDistanceTo(assembler.place.start) < 6 } }
            .forEach { beaconStart ->
                val beacon = Utils.getBuilding(beaconStart, BuildingType.BEACON)
                val newState = current.addBuilding(beacon)
                if (newState != null) {
                    val newScore =
                        if (unit == "processing-unit" || unit == "electronic-circuit" || unit == "steel-plate")
                            scoreManager.calculateScoreForItem(newState, unit, recipeTree) else
                            scoreManager.calculateScoreForBuilding(Pair(newState, beacon), unit)
                    result.add(Pair(newScore, newState))
                }
            }

        return result
    }

    private fun findLeastPerformed(current: State, unit: String): List<Building> {
        val result = mutableListOf<Building>()
        val buildings = if (unit == "steel-plate")
            current.buildings.filterIsInstance<Smelter>().filter { it.recipe == "steel-plate" }
        else
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

    fun stepUnit(
        start: Cell,
        current: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        unit: String
    ): Pair<State?, Double?> {
        val withConnections =
            TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, unit) }
        val type = if (unit in setOf(
                "stone-brick",
                "copper-plate",
                "iron-plate",
                "steel-plate"
            )
        ) BuildingType.SMELTER else BuildingType.ASSEMBLER
        val building = Utils.getBuilding(
            start,
            type,
            recipe = unit
        )
        val withBuilding = current.addBuilding(building) ?: return Pair(null, null)
        // add chest and inserters
        val planeChest = planeChests(withBuilding, field, building, unit)
        planeChest.map { it to building }.forEach { withConnections.add(it) }
        withConnections.forEach {
            val bestWire = it
            val bestWireState = bestWire.first
            val bestWireScore = scoreManager.calculateScoreForItem(bestWireState, unit, recipeTree)
            return Pair(bestWireState, bestWireScore)
        }
        return Pair(null, null)
    }

    fun stepAllUnit(
        start: Cell,
        current: State,
        field: Field,
        recipeTree: Map<String, ProcessedItem>,
        unit: String
    ): SortedSet<Pair<State?, Double?>> {

        val result = TreeSet<Pair<State?, Double?>> { a, b ->
            if (a.second!! != b.second!!) a.second!!.compareTo(b.second!!) else
                a.hashCode().compareTo(b.hashCode())
        }

        val type = if (unit in setOf(
                "stone-brick",
                "copper-plate",
                "iron-plate",
                "steel-plate"
            )
        ) BuildingType.SMELTER else BuildingType.ASSEMBLER
        val building = Utils.getBuilding(
            start,
            type,
            recipe = unit
        )
        val withBuilding = current.addBuilding(building) ?: return sortedSetOf(Pair(null, null))
        // add chest and inserters
        val planeChest = planeChests(withBuilding, field, building, unit)
        planeChest.forEach {

            val bestWireScore = scoreManager.calculateScoreForItem(it, unit, recipeTree)
            result.add(Pair(it, bestWireScore))
        }
        return result
    }


    private fun planeUpgrade(recipeTree: Map<String, ProcessedItem>, field: Field, best: State): State {
        var current = best
        //upgrade productivity
        current = upgradeManager.upgradeProductivity(current, recipeTree, field)
        //upgrade robots
//        current = upgradeManager.upgradeRobotsTwo(current, recipeTree, field)
//        current = upgradeManager.upgradeRobots(current, recipeTree, field)


        return current
    }

    private fun planeDowngrade(upgrade: State, fieldPrep: Field, recipeTree: Map<String, ProcessedItem>): State {
        //downgrade productivity modules - done by hand
        //downgrade speed modules
        return upgradeManager.downgradeSpeed(upgrade, recipeTree, fieldPrep)
    }

    companion object {
        private val log = KotlinLogging.logger {}

        val isSmelter = true
//        val smelterType = "copper"
//        val smelterOre = "copper"

        //        val smelterType = "steel"
        val smelterType = "iron"
        val smelterOre = "iron"

        @JvmStatic
        fun main(args: Array<String>) {
            log.info { "Start" }
            smelter()
        }

        private fun smelter() {
            val tree = TechnologyTreePlanner.smelterTree()
            GlobalPlanner().planeGlobal(
                tree,
                BluePrintFieldExtractor.FIELD_SMELTER,
                BluePrintFieldExtractor.FIELD_SMELTER
            )
        }

        private fun science() {
            val tree = TechnologyTreePlanner.scienceRoundTree()
//            GlobalPlanner().planeGlobal(tree, BluePrintFieldExtractor.FIELD_PREP, BluePrintFieldExtractor.FIELD_FULL)
        }
    }
}