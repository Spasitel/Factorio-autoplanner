package ru.spasitel.factorioautoplanner.planner

import com.google.gson.Gson
import ru.spasitel.factorioautoplanner.data.*
import ru.spasitel.factorioautoplanner.data.auto.BlueprintDTO
import ru.spasitel.factorioautoplanner.data.building.*
import ru.spasitel.factorioautoplanner.formatter.BluePrintFieldExtractor
import ru.spasitel.factorioautoplanner.formatter.Formatter
import java.util.*
import kotlin.math.roundToInt
import kotlin.math.sqrt

class GlobalPlanner {


    fun planeGlobal(recipeTree: Map<String, ProcessedItem>, fieldBlueprint: String) {
        val decodeField = Formatter.decode(fieldBlueprint)
        val dto = Gson().fromJson(decodeField, BlueprintDTO::class.java)
        val field = BluePrintFieldExtractor().transformBlueprintToField(dto)

        var double = true
        var min = 0.0
        var max = 1.0
        var best: State? = null
        while (max - min > 0.05) {
            val mid = (max + min) / 2
            val greedy = planeGreedy(recipeTree, field, mid)
            if (greedy != null) {
                min = calculateScore(greedy, recipeTree)
                best = greedy
                if (double) {
                    max = min * 3
                }
            } else {
                double = false
                max = mid
            }
        }

        if (best == null) {
            println("No solution found")
            return
        }
        val upgrade = planeUpgrade(recipeTree, field, best)

        planeDowngrade(upgrade)

    }


    private fun planeGreedy(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        var state = planeSpecial(recipeTree, field, mid) ?: return null

        val done =
            mutableSetOf("processing-unit", "electronic-circuit", "battery", "plastic-bar", "electric-engine-unit")
        while (recipeTree.keys.minus(done).isNotEmpty()) {
            val item = calculateNextItem(recipeTree, done)
            state = planeGreedyItem(recipeTree, item, state, field, mid) ?: return null
            state = planeRoboports(state, field) ?: return null
            done.add(item)
        }
        return state
    }

    private fun planeSpecial(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        val processors = planeProcessingUnit(recipeTree, field, mid) ?: return null
        val circuits = planeCircuits(processors, recipeTree, field, mid) ?: return null
        val batteries = planeBatteries(circuits, recipeTree, field, mid) ?: return null
        val engines = planeEngines(batteries, recipeTree, field, mid) ?: return null
        return engines
    }

    private fun planeProcessingUnit(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        var score = 0.0
        var current = field.state
        while (score < mid) {
            var next: State? = null
            var nextScore: Double? = null
            if (score > 0) {
                next = planeBeacon(current, field, "processing-unit")
                nextScore = next?.let { calculateScoreForItem(it, "processing-unit", recipeTree) }
            }
            val starts = placesForItem(current, field, "processing-unit", recipeTree)
            loop@ for ((attempts, start) in starts.withIndex()) {
                // sort by score
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
                        TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, "electronic-circuit") }
                    val greenPositions = placeConnected(blue.first, blue.second, field, "electronic-circuit")
                    for (greenPosition in greenPositions) {
                        val greenBuilding = Utils.getBuilding(
                            greenPosition,
                            BuildingType.ASSEMBLER,
                            recipe = "electronic-circuit"
                        )
                        val green = blue.first.addBuilding(greenBuilding) ?: continue
                        val planeConnection = planeConnection(green, field, greenBuilding, blue.second) ?: continue
                        // add chest and inserters
                        val planeChest = planeChests(planeConnection, field, greenBuilding, "electronic-circuit", false)
                        planeChest.map { g -> g to greenBuilding }.forEach { g -> greenWithConnections.add(g) }
                    }
                    //add copper wire

                    for (green in greenWithConnections) {
                        val wireWithConnections =
                            TreeSet<Pair<State, Building>> { a, b -> compareForUnit(a, b, "copper-cable") }
                        val wirePositions = placeConnected(green.first, green.second, field, "copper-cable")
                        for (wirePosition in wirePositions) {
                            val wireBuilding = Utils.getBuilding(
                                wirePosition,
                                BuildingType.ASSEMBLER,
                                recipe = "copper-cable"
                            )
                            val wire = green.first.addBuilding(wireBuilding) ?: continue
                            val planeConnection = planeConnection(wire, field, wireBuilding, green.second) ?: continue
                            // add chest and inserters
                            val planeChest = planeChests(planeConnection, field, wireBuilding, "copper-cable", false)
                            planeChest.map { g -> g to wireBuilding }.forEach { g -> wireWithConnections.add(g) }
                        }

                        if (wireWithConnections.isNotEmpty()) {
                            val bestWire = wireWithConnections.first()
                            val bestWireState = bestWire.first
                            val bestWireScore = calculateScoreForItem(bestWireState, "processing-unit", recipeTree)
                            if (next == null || bestWireScore > nextScore!!) {
                                nextScore = bestWireScore
                                next = bestWireState
                            }
                            break@loop
                        }
                    }
                }
            }

            if (next == null) {
                return null
            }
            current = next
            score = nextScore!!
        }
        return current
    }

    private fun planeChests(
        planeLiquid: State,
        field: Field,
        procBuilding: Building,
        s: String,
        withOutput: Boolean = true
    ): List<State> {
        val withInputChest =
            GreedyPlanner().addChests(planeLiquid, procBuilding, BuildingType.REQUEST_CHEST, field = field)
        if (withOutput) {
            val result = mutableListOf<State>()
            for (withInputChestState in withInputChest) {
                val withOutputChest =
                    GreedyPlanner().addChests(
                        withInputChestState,
                        procBuilding,
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

    private fun calculateScoreForItem(state: State, unit: String, recipeTree: Map<String, ProcessedItem>): Double {
        val recipe = recipeTree[unit]!!
        //TODO: battery, stone brick, space science pack
        val buildings = state.buildings.filter { it.type == BuildingType.ASSEMBLER && (it as Assembler).recipe == unit }
        val result = buildings.sumOf { calculateScoreForBuilding(Pair(state, it), unit) }
        return result / recipe.productivity()
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
        var freeCells = current.freeCells.value
        if (unit == "processing-unit") {
            freeCells = freeCells.filter { it.y < 50 }.toHashSet()//todo
        }
        return freeCells.sortedBy {
            chestsToValue.map { (t, u) ->
                getDistance(it, t) * u
            }.sum()
        }
    }

    private fun planeLiquid(current: State, field: Field, proc: Building, liquid: String): State? {
        val start = proc.place.start.down().right().move((proc as Assembler).direction!!)
        return OilPlanner().planeLiquid(liquid, start, current, field)
    }

    private fun getDistance(from: Cell, to: Cell): Double {
        return sqrt((from.x - to.x) * (from.x - to.x) + (from.y - to.y) * (from.y - to.y).toDouble())
    }

    private fun compareForUnit(a: Pair<State, Building>, b: Pair<State, Building>, unit: String): Int {
        val aScore = calculateScoreForBuilding(a, unit)
        val bScore = calculateScoreForBuilding(b, unit)
        if (aScore != bScore) {
            return bScore.compareTo(aScore)
        }
        val aSize = a.first.freeCells.value.size
        val bSize = b.first.freeCells.value.size
        if (aSize != bSize) {
            return bSize.compareTo(aSize)
        }

        return a.hashCode().compareTo(b.hashCode())
    }

    private fun calculateScoreForBuilding(pair: Pair<State, Building>, unit: String): Double {
        val building = pair.second
        if (building.type == BuildingType.ASSEMBLER) {
            val assembler = building as Assembler
            var start = if (unit in TechnologyTreePlanner.productivity_module_limitation) {
                0.4
            } else {
                3.0
            }
            start += pair.first.buildings.filter {
                it.type == BuildingType.BEACON && it.place.start.maxDistanceTo(assembler.place.start) < 6
            }.size * 0.5
            return start

        } else if (building.type == BuildingType.BEACON) {
            return pair.first.buildings.filter {
                it.type == BuildingType.ASSEMBLER &&
                        (it as Assembler).recipe == unit
                        && it.place.start.maxDistanceTo(building.place.start) < 6
            }.size * 0.5

        }
        throw RuntimeException("Unknown building type: ${building.type}")
    }


    private fun buildingsForItem(recipeTree: Map<String, ProcessedItem>, unit: String, start: Cell): List<Building> {
        TODO("Not yet implemented")
    }


    private fun planeBeacon(current: State, field: Field, unit: String): State? {
        //TODO blue and green
        var score = 0.0
        var best: State? = null

        current.buildings.filter { b -> b.type == BuildingType.ASSEMBLER && (b as Assembler).recipe == unit }
            .forEach { assembler ->
                current.freeCells.value.filter { it.maxDistanceTo(assembler.place.start) < 6 }.forEach { beaconStart ->
                    val beacon = Utils.getBuilding(beaconStart, BuildingType.BEACON)
                    val newState = current.addBuilding(beacon)
                    if (newState != null) {
                        val newScore = calculateScoreForBuilding(Pair(newState, beacon), unit)
                        if (best == null || newScore > score
                            || (newScore == score && newState.freeCells.value.size > best!!.freeCells.value.size)
                        ) {
                            score = newScore
                            best = newState
                        }
                    }
                }
            }
        return best
    }

    private fun planeEngines(
        plastic: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        mid: Double
    ): State? {
        TODO()
    }


    private fun planeBatteries(
        circuits: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        mid: Double
    ): State? {
        TODO()
    }

    private fun planeCircuits(
        processors: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        mid: Double
    ): State? {
        TODO()
    }


    private fun calculateNextItem(recipeTree: Map<String, ProcessedItem>, done: Set<String>): String {
        TODO("Not yet implemented")
    }

    private fun planeGreedyItem(
        recipeTree: Map<String, ProcessedItem>,
        item: String,
        state: State,
        field: Field,
        mid: Double
    ): State? {
        TODO("Not yet implemented")
    }

    private fun planeRoboports(state: State, field: Field): State? {
        TODO("Not yet implemented")
    }

    private fun calculateScore(greedy: State, recipeTree: Map<String, ProcessedItem>): Double {
        TODO("Not yet implemented")
    }

    private fun planeUpgrade(recipeTree: Map<String, ProcessedItem>, field: Field, best: State): State {
        TODO("Not yet implemented")
    }

    private fun planeDowngrade(upgrade: State) {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val tree = TechnologyTreePlanner.scienceRoundTree()
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
            println(amountMap)

            GlobalPlanner().planeGlobal(tree, BluePrintFieldExtractor.FIELD_4)
        }
    }
}