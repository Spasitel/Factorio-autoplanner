package ru.spasitel.factorioautoplanner.planner

import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.Building
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import ru.spasitel.factorioautoplanner.data.building.Inserter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.sign

class UpgradeManager {

    /**
     * Remove close buildings and try to replace them with better ones
     * @param state current state
     * @param attemptsLimit number of attempts to upgrade
     */
    fun upgradeByReplacement(state: State, attemptsLimit: Int): State {
        var best = state
        attempts = 0

        var size = 1
        var lastDelete: List<Building> = emptyList()
        var prevDate = Date()
        while (attempts < attemptsLimit) {
            val deletedSet =
                TreeSet { o1: State, o2: State ->
                    val freeDiff = o1.freeCells.size - o2.freeCells.size
                    val valueDiff = (o1.score.value - o2.score.value).sign.toInt()
                    if (freeDiff != 0) freeDiff
                    else if (valueDiff != 0) valueDiff
                    else o1.hashCode() - o2.hashCode()
                }
            var deleted: List<Building>? = findNextToDelete(best, lastDelete) ?: return best
            while (deleted != null && deleted.size == size) {
                val newState = removeBuildings(best, deleted)

                deletedSet.add(newState)
                lastDelete = deleted
                deleted = findNextToDelete(best, deleted)
            }
            for (newState in deletedSet.descendingSet()) {
                val date = Date()
                if (date.time - prevDate.time > 60000) {
                    printCurrentState(date, size, newState)
                    prevDate = date
                }
                val newBest = findBestReplacements(newState)
                if (newBest.score.value > best.score.value + DELTA) {
                    printCurrentState(date, size, newState)
                    best = newBest
                    lastDelete = emptyList()
                    size = 0
                    Utils.printBest(best)
                    break
                } else if (newBest.score.value < best.score.value - DELTA) {
                    throw IllegalStateException("Score decreased")
                }
            }
            size++
        }
        return best
    }

    private fun printCurrentState(date: Date, size: Int, newState: State) {
        val sdf = SimpleDateFormat("dd/M/yyyy hh:mm:ss")
        val currentDate = sdf.format(date)
        println("$currentDate == $attempts, size: $size, free cells: ${newState.freeCells.size}")
    }

    private fun findBestReplacements(state: State): State {
        val freeCells = state.freeCells
        val closestFreeCell = freeCells.minByOrNull { it.x * state.size.y + it.y } ?: return state
        var localBest = state
        var localBestScore = state.score.value
        for (type in TYPES) {
            val newStates = GreedyPlanner().generateNewStates(type, state, closestFreeCell)
            for (newState in newStates) {
                attempts++
                var current = newState
                if (newState.freeCells.isNotEmpty()) {
                    current = findBestReplacements(newState)
                }
                if (current.score.value > localBestScore) {
                    localBestScore = current.score.value
                    localBest = current
                }
            }
        }
        return localBest
    }

    private fun removeBuildings(best: State, deleted: List<Building>): State {
        var result = best
        for (building in deleted) {
            result = result.removeBuilding(building)
        }
        for (building in best.buildings) {
            if (building.type == BuildingType.INSERTER && isInserterUseless(building as Inserter, result)) {
                result = result.removeBuilding(building)
            }
        }
        for (building in best.buildings) {
            if ((building.type == BuildingType.PROVIDER_CHEST || building.type == BuildingType.REQUEST_CHEST)
                && isChestUseless(building, result)
            ) {
                result = result.removeBuilding(building)
            }
        }



        return result
    }


    private fun isChestUseless(building: Building, best: State): Boolean {
        val start = building.place.start
        val up = start.up()
        if (best.map[up] != null && best.map[up]!!.type == BuildingType.INSERTER && !(best.map[up]!! as Inserter).alongX()) return false
        val down = start.down()
        if (best.map[down] != null && best.map[down]!!.type == BuildingType.INSERTER && !(best.map[down]!! as Inserter).alongX()) return false
        val left = start.left()
        if (best.map[left] != null && best.map[left]!!.type == BuildingType.INSERTER && (best.map[left]!! as Inserter).alongX()) return false
        val right = start.right()
        return !(best.map[right] != null && best.map[right]!!.type == BuildingType.INSERTER && (best.map[right]!! as Inserter).alongX())
    }

    private fun isInserterUseless(building: Inserter, best: State): Boolean {
        val start = building.place.start
        val directionX = if (building.alongX()) 1 else 0
        val directionY = if (building.alongX()) 0 else 1
        best.map[Cell(start.x + directionX, start.y + directionY)] ?: return true
        best.map[Cell(start.x - directionX, start.y - directionY)] ?: return true

        return false
    }

    fun findNextToDelete(state: State, deleted: List<Building>): List<Building>? {
        // find next building to delete
        var size = 1.coerceAtLeast(deleted.size)
        var start = if (deleted.isEmpty()) Cell(0, 0) else deleted.last().place.start
        val current = deleted.toMutableList()
        if (current.isNotEmpty()) current.removeLast()
        var sizedUp = false
        while (current.size < size) {
            val next = getClosestBuilding(state, start, current)
            if (next == null) {
                if (current.isNotEmpty()) {
                    val last = current.removeLast()
                    start = last.place.start
                } else {
                    size++
                    start = Cell(0, 0)
                    if (sizedUp) return null
                    sizedUp = true
                }
            } else {
                current.add(next)
                start = next.place.start
            }
        }
        return current
    }

    private fun getClosestBuilding(state: State, startPosition: Cell, current: MutableList<Building>): Building? {
        val maxX =
            if (current.isEmpty()) state.size.x else state.size.x.coerceAtMost(current.last().place.start.x + distance)

        for (x in startPosition.x..maxX) {
            val minY =
                if (x == startPosition.x) startPosition.y + 1 else if (current.isEmpty()) 0 else 0.coerceAtLeast(current.last().place.start.y - distance)
            val maxY =
                if (current.isEmpty()) state.size.y else state.size.y.coerceAtMost(current.last().place.start.y + distance)
            for (y in minY..maxY) {
                val cell = Cell(x, y)
                val building = state.map[cell]
                if (building != null && building.place.start == cell && building.type in TYPES) {
                    return building
                }
            }
        }
        return null
    }


    companion object {
        const val DELTA = 0.01
        val TYPES = listOf(
            BuildingType.BEACON,
            BuildingType.SMELTER,
//            BuildingType.ASSEMBLER,
        )
        var attempts = 0
        var distance = 7
    }


}