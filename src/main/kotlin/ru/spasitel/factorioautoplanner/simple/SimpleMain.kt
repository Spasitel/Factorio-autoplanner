package ru.spasitel.factorioautoplanner.simple

import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.formatter.Formatter
import ru.spasitel.factorioautoplanner.simple.building.*
import java.util.*
import java.util.function.Consumer
import kotlin.math.abs

object SimpleMain {
    const val SIZE = 18
    const val LIMIT: Long = 4000000

    var debug = false
    private var max = 0.0
    private var best: State? = null

    @JvmStatic
    fun main(args: Array<String>) {
        var start: State? = State(emptySet(), .0, 0, Array(SIZE) { arrayOfNulls(SIZE) })
        start = start?.addBuilding(EmptySpace(0, 0, 1))
        start = start?.addBuilding(EmptySpace(SIZE - 1, 0, 1))
        start = start?.addBuilding(EmptySpace(0, SIZE - 1, 1))
        start = start?.addBuilding(EmptySpace(SIZE - 1, SIZE - 1, 1))
        max = 0.0
        best = start
        val next = generateNext(start)
        next.forEach(Consumer { s: State ->
            val scoreByArea = s.score / s.area
            println(
                "Calculating " + s.buildings.stream()
                    .filter { b: Building -> b.type == Type.SMELTER || b.type == Type.BEACON }.findAny()
            )
            calculateForStart(scoreByArea, s)
        })
        printBest()
    }

    private fun calculateForStart(score: Double, start: State) {
        val stateScores = TreeMap<Double, MutableList<State>>()
        stateScores[score] = listOf(start).toMutableList()
        var localMax = 0.0
        var bestCount: Long = 0
        var count: Long = 0
        while (!stateScores.isEmpty()) {
            count++
            if (count > LIMIT) return
            val (key, value) = stateScores.lastEntry()
            val state = value[0]
            if (value.size == 1) stateScores.remove(key) else {
                value.removeAt(0)
            }
            val next = generateNext(state)
            if (next.isEmpty() && state.score > localMax) {
                localMax = state.score
                System.out.printf("New best %f : %d/%d%n", localMax, count - bestCount, count)
                if (state.score > max) {
                    max = state.score
                    best = state
                    printBest()
                }
                bestCount = count
            }
            next.forEach(Consumer { s: State ->
                val scoreByArea = s.score / s.area
                if (stateScores.containsKey(scoreByArea)) {
                    stateScores[scoreByArea]!!.add(s)
                } else {
                    stateScores[scoreByArea] = ArrayList(listOf(s))
                }
            })
        }
    }

    private fun printBest() {
        println(best)
        var bCount = 1
        var json = StringBuilder(
            String.format(
                Utils.START_JSON,
                best.hashCode().div(1000).mod(10),
                best.hashCode().div(100).mod(10),
                best.hashCode().div(10).mod(10),
                best.hashCode().mod(10),
            )
        )
        for (b in best!!.buildings) {
            if (b.type == Type.EMPTY) continue
            json.append(b.toJson(bCount))
            bCount++
        }
        json = StringBuilder(json.substring(0, json.length - 1))
        json.append(Utils.END_JSON)
        println(Formatter.encode(json.toString()))
    }

    private fun generateNext(state: State?): List<State> {
        var startX = 0
        var startY = -2
        for (b in state!!.buildings) {
            if ((b.type == Type.SMELTER || b.type == Type.BEACON)
                && (b.x > startX || b.x == startX && b.y > startY)
            ) {
                startX = b.x
                startY = b.y
            }
        }
        val result: MutableList<State> = ArrayList()
        try {
            for (x in startX..SIZE - 3) {
                for (y in (if (x == startX) startY + 3 else 0)..SIZE - 3) {
                    val beacon = addBuilding(state, Beacon(x, y))
                    if (beacon != null) {
                        result.add(beacon)
                        val smelter = addBuilding(state, Smelter(x, y))
                        if (smelter != null) result.add(smelter)
                    }
                }
            }
        } catch (e: IllegalStateException) {
            //
        }
        return result
    }

    private fun addBuilding(state: State?, building: Building): State? {
        var missed: Building? = null
        if (building.x > 1 && building.y > 2) {
            missed = Beacon(building.x - 2, building.y - 3)
        } else if (building.x > 2 && building.y <= 2) {
            missed = Beacon(building.x - 3, SIZE - 3)
        }
        check(!(missed != null && state!!.addBuilding(missed) != null))
        return calculateState(state, building)
    }

    private fun calculateState(state: State?, building: Building): State? {
        val stateWithBuilding = state!!.addBuilding(building) ?: return null

        // Потом что можно поставить сундуки
        val withChests = ChestPlannerUtils.calculateChests(stateWithBuilding) ?: return null
        var score = state.score
        var area = 0
        if (building.type == Type.SMELTER || building.type == Type.BEACON) {
            area = building.x * 18 + building.y + 8
            if (building.type == Type.SMELTER) {
                score += 0.7
            }
            for (b in state.buildings) {
                if (b.type != Type.SMELTER && b.type != Type.BEACON) continue
                area += if (b.y > building.y) {
                    0.coerceAtLeast(3 * (b.x - building.x + 3))
                } else {
                    0.coerceAtLeast(3 * (b.x - building.x + 2))
                }
                if (b.type == building.type) continue
                if (abs(b.x - building.x) < 6 && abs(b.y - building.y) < 6) score += 0.5
            }
        }
        return State(withChests.buildings, score, area, withChests.map)
    }

    fun copyOf(map: Array<Array<Building?>>): Array<Array<Building?>> {
        val newMap: Array<Array<Building?>> = Array(SIZE) { arrayOfNulls(SIZE) }
        for (x in 0 until SIZE) {
            newMap[x] = map[x].copyOf(SIZE)
        }
        return newMap
    }

    @JvmStatic
    fun isInteract(prev: Building, building: Building): Boolean {
        val left: Building
        val right: Building
        if (prev.x < building.x || prev.x == building.x && prev.y < building.y) {
            left = prev
            right = building
        } else {
            left = building
            right = prev
        }
        return if (left.type == Type.BEACON) {
            !(left.x <= right.x - 3 || left.y <= right.y - 3 || left.y >= right.y + 3)
        } else {
            !(left.x <= right.x - 5 || left.y <= right.y - 3 || left.y >= right.y + 3 || left.y == right.y - 2 && left.x <= right.x - 3)
        }
    }
}