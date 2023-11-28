package ru.spasitel.factorioautoplanner.planner

import ru.spasitel.factorioautoplanner.data.*
import ru.spasitel.factorioautoplanner.data.building.*
import java.util.*
import kotlin.math.abs
import kotlin.math.sign

class OilPlanner {

    fun planeOil(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        if (recipeTree["petroleum-gas"] == null) return field.state
        val (oil, heavyOil, lightOil) = calculateOil(recipeTree, mid)

        val oilRefinery = planeRefinery(oil, field)


        return oilRefinery
    }

    private fun planeRefinery(oil: Double, field: Field): State? {
        val cell = calculateRefineryPosition(field, field.liquids["crude-oil"]?.first()!!)
        if (cell == null) {
            println("No place for refinery")
            return null
        }
        val place = Place(Utils.cellsForBuilding(cell, BuildingType.OIL_REFINERY.size), cell)
        loop@ for (direction in 0..6 step 2) {
            val refinery = OilRefinery(place, direction)
            var state: State = field.state.addBuilding(refinery) ?: continue@loop
            for (input in refinery.getInputs()) {
                state = planeLiquid(input.key, input.value, state, field) ?: continue@loop
            }
            return state
        }
        return null
    }

    private fun planeLiquid(liquid: String, start: Cell, state: State, field: Field): State? {
        //find all pipes with this liquid
        //calculate all possible connections cells
        val connections = calculateConnections(liquid, state, field)

        //A-star to find path from value to connection cell
        return aStar(state, start, connections, liquid, field)
    }

    private fun aStar(state: State, start: Cell, connections: Set<Cell>, liquid: String, field: Field): State? {
        val states = TreeSet<AStarState> { o1: AStarState, o2: AStarState ->
            val freeDiff = (o1.score - o2.score).sign.toInt()
            val valueDiff = (o2.distance - o1.distance).sign.toInt()
            if (freeDiff != 0) freeDiff
            else if (valueDiff != 0) valueDiff
            else o1.hashCode() - o2.hashCode()
        }
        states.add(AStarState(state, start, 0.0, 0.0))
        var count = 0
        val done = HashSet<Pair<Cell, Int>>()
        while (true) {
            val current = states.pollFirst() ?: return null
            count++
            //generate next steps
            val next = calculateNext(current, liquid, connections, field, done)


            if (states.size.div(1000) - (states.size + next.size).div(1000) != 0) {
                println("A-star step $count, states size ${states.size}, current score ${current.score}, current penalty ${current.penalty}, current distance ${current.distance}")
            }
            //check if we have connection cell
            next.firstOrNull { calculateDistance(it.current, connections) == 1.0 }?.let {
                return it.state
            }
            states.addAll(next)
        }

    }

    private fun calculateNext(
        current: AStarState,
        liquid: String,
        connections: Set<Cell>,
        field: Field,
        done: HashSet<Pair<Cell, Int>>
    ): Set<AStarState> {
        val next = HashSet<AStarState>()
        val cell = current.current
        val directions = if (current.state.map[cell]?.type == BuildingType.UNDERGROUND_PIPE) {
            setOf((current.state.map[cell] as UndergroundPipe).direction)
        } else {
            setOf(0, 2, 4, 6)
        }


        for (direction in directions) {
            val nextCell = cell.move(direction)
            if (done.contains(Pair(nextCell, direction))) continue
            if (current.state.map.containsKey(nextCell)) continue
            val pipe = Utils.getBuilding(nextCell, BuildingType.PIPE, liquid = liquid)
            current.state.addBuilding(pipe)?.let {
                next.add(
                    AStarState(
                        it,
                        nextCell,
                        calculateDistance(nextCell, connections),
                        current.penalty + calculatePenalty(pipe, field)
                    )
                )
            }

            val undergroundPipe = Utils.getBuilding(
                nextCell,
                BuildingType.UNDERGROUND_PIPE,
                liquid = liquid,
                direction = (direction + 4).mod(8)
            )
            current.state.addBuilding(undergroundPipe)?.let { enter ->
                var exit = undergroundPipe.place.start.move(direction)
                for (length in 1..9) {
                    val nextExit = exit.move(direction)
                    if (!done.contains(Pair(exit, direction)) && (
                                enter.map[nextExit] == null ||
                                        enter.map[nextExit]!!.type == BuildingType.UNDERGROUND_PIPE ||
                                        enter.map[nextExit]!!.type == BuildingType.PIPE ||
                                        enter.map[nextExit]!!.type == BuildingType.STORAGE_TANK)
                    ) {
                        val undergroundExit = Utils.getBuilding(
                            exit,
                            BuildingType.UNDERGROUND_PIPE,
                            liquid = liquid,
                            direction = direction
                        )
                        enter.addBuilding(undergroundExit)?.let {
                            next.add(
                                AStarState(
                                    it,
                                    exit,
                                    calculateDistance(exit, connections),
                                    current.penalty + calculatePenalty(undergroundExit, field)
                                )
                            )
                        }
                    }
                    exit = nextExit
                }
            }
            done.add(Pair(nextCell, direction))
        }
        return next
    }

    private fun calculatePenalty(pipe: Building, field: Field): Double {
        val start = pipe.place.start
        for (i in -3..7) {
            if (Utils.isBetween(start, field.chestField, i)) {
                return 15.0 - i
            }
        }
        if (Utils.isBetween(start, field.electricField)) {
            return 1.5
        }
        return 0.5
    }

    private fun calculateDistance(start: Cell, connections: Set<Cell>): Double {

        var minDistance = Int.MAX_VALUE
        for (connection in connections) {
            val distance = abs(start.x - connection.x) + abs(start.y - connection.y)
            if (distance < minDistance) {
                minDistance = distance
            }
        }
        return minDistance.toDouble()
    }

    private fun calculateConnections(liquid: String, state: State, field: Field): Set<Cell> {
        val connections = HashSet<Cell>()
        state.buildings.forEach {
            if (it.type == BuildingType.PIPE && (it as Pipe).liquid == liquid) {
                connections.add(it.place.start)
            }
            if (it.type == BuildingType.UNDERGROUND_PIPE && (it as UndergroundPipe).liquid == liquid) {
                connections.add(it.place.start)
                var value = it.place.start
                while (!(state.map[value]?.type == BuildingType.UNDERGROUND_PIPE
                            && (state.map[value] as UndergroundPipe).liquid == liquid) &&
                    (state.map[value] as UndergroundPipe).direction == (it.direction + 4).mod(8)
                ) {
                    connections.add(value)
                    value = value.move((it.direction + 4).mod(8))
                }
            }
            if (it.type == BuildingType.STORAGE_TANK && (it as StorageTank).liquid == liquid) {
                connections.addAll(it.connections())
            }
        }

        field.liquids[liquid]?.let { connections.addAll(it) }
        return connections
    }

    private fun calculateRefineryPosition(field: Field, start: Cell): Cell? {
        if (start.y < start.x) {
            for (x in start.x downTo 0) {
                val startY = if (x == start.x) start.y else 0
                for (y in startY until field.chestField.first.y - 11) {
                    val cells = Utils.cellsForBuilding(Cell(x, y), BuildingType.OIL_REFINERY.size)
                    if (field.state.map.keys.intersect(cells).isEmpty()) {
                        return Cell(x, y)
                    }
                }
            }
        }
        //todo
//        for (x in 0 until field.chestField.first.x - 11) {
//            for (y in start.y downTo 0) {
//                val startX = if (y == start.y) start.x else 0
//                val cells = Utils.cellsForBuilding(Cell(x, y), BuildingType.OIL_REFINERY.size)
//                if (field.state.map.keys.intersect(cells).isEmpty()) {
//                    return Cell(x, y)
//                }
//            }
//        }

        return null
    }

    private fun calculateOil(recipeTree: Map<String, ProcessedItem>, mid: Double): Triple<Double, Double, Double> {
        val heavy = recipeTree["heavy-oil"]?.amount ?: 0.0
        val light = recipeTree["light-oil"]?.amount ?: 0.0
        val petroleum = recipeTree["petroleum-gas"]?.amount ?: throw Exception("No petroleum-gas in recipe tree")
        return Triple(
            (heavy * 20 / 39 + light * 80 / 117 + petroleum * 40 / 39) * mid,
            (-heavy * 34 / 39 + light * 20 / 117 + petroleum * 10 / 39) * mid,
            (-heavy * 11 / 26 - light * 22 / 39 + petroleum * 17 / 26) * mid
        )
    }

}

data class AStarState(
    val state: State,
    val current: Cell,
    val distance: Double,
    val penalty: Double
) {
    val score = distance + penalty
}
