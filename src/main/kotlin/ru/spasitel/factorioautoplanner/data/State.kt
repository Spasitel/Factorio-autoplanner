package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.building.Beacon
import ru.spasitel.factorioautoplanner.data.building.Building
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import ru.spasitel.factorioautoplanner.data.building.LiquidConnectionsBuilding
import kotlin.math.max


data class State(
    val buildings: Set<Building>,
    val map: Map<Cell, Building>,
    val size: Cell,
    val performanceMap: Map<Cell, Double> = HashMap(),
    val freeCells: Set<Cell> = allCells(size),
    val emptyCountScore: Int = (size.x - 1) * (size.y - 1) * 32 + (size.x + size.y - 2) * 16
) {
    /*
        val emptyScore: Lazy<Int> = lazy {
            val scoreMap: MutableMap<Cell, Int> = HashMap()
            var result = 0
            for (x in 0 until size.x) {
                for (y in 0 until size.y) {
                    val cell = Cell(x, y)
                    if (map[cell] == null) {
                        var current = 0
                        if (map[cell.right()] == null) {
                            current++
                        }
                        if (map[cell.down()] == null) {
                            current++
                        }
                        if (map[cell.left()] == null) {
                            current++
                        }
                        if (map[cell.up()] == null) {
                            current++
                        }
                        if (current != 0) {
                            scoreMap[cell] = current
                        }
                    }
                }
            }
            for (cell in scoreMap.keys) {
                if (scoreMap[cell.right()] != null)
                    result += scoreMap[cell]!! * scoreMap[cell.right()]!!
                if (scoreMap[cell.down()] != null)
                    result += scoreMap[cell]!! * scoreMap[cell.down()]!!
    //            if (scoreMap[cell.left()] != null)
    //                result += scoreMap[cell]!! * scoreMap[cell.left()]!!
    //            if (scoreMap[cell.up()] != null)
    //                result += scoreMap[cell]!! * scoreMap[cell.up()]!!
            }
            result
        }
    */
    val number: Int = count++

    val area: Lazy<Int> = lazy {

        (size.x - 2) * (size.y - 2) - freeCells.size
    }


    val production: Lazy<Double> = lazy {
        var score = 0.0
//        for (b in buildings) {
//            if (b.type == BuildingType.SMELTER) {
//                score += 4
//            }
//            if (b.type == BuildingType.BEACON) {
//                for (affected in buildings) {
//
//                    if (affected.type != BuildingType.SMELTER) continue
//                    if (abs(b.place.start.x - affected.place.start.x) < 6
//                        && abs(b.place.start.y - affected.place.start.y) < 6
//                    ) {
//                        score += 1
//                    }
//                }
//            }
//        }
        score
    }

    val score: Lazy<Double> = lazy {
        production.value / area.value
    }

    override fun toString(): String {
        val str = StringBuilder("=========================================================\n")
        var smelter = 0
        var beacon = 0
        for (b in buildings) {
            if (b.type == BuildingType.SMELTER) smelter++
            if (b.type == BuildingType.BEACON) beacon++
        }
        str.append("Score: ").append(production.value).append(" Area: ").append(area.value).append(" Efficiency: ")
            .append(score.value)
            .append(" Buildings (s/b): ").append(smelter).append("/").append(beacon).append("\n")

        for (y in 0 until size.y) {
            for (x in 0 until size.x) {

                val building = map[Cell(x, y)]
                if (building == null) {
                    str.append('.')
                } else {
                    str.append(building.symbol)
                }
            }
            str.append("\n")
        }
        str.append(
            "State{" +
                    "buildings=" + buildings +
                    ", score=" + score.value +
                    ", area=" + area.value +
                    '}'
        )
        return str.toString()
    }

    fun addBuilding(building: Building): State? {

        val newFreeCells = HashSet(freeCells)
        val newMap = map.toMutableMap()
        val newBuildings: MutableSet<Building> = HashSet(buildings)
        val newPerformanceMap = performanceMap.toMutableMap()

        return addBuildingTo(building, newMap, newBuildings, newPerformanceMap, newFreeCells)
    }

    fun addBuildings(buildingsToAdd: List<Building>): State? {
        var state = this
        val newFreeCells = HashSet(freeCells)
        val newMap = map.toMutableMap()
        val newBuildings: MutableSet<Building> = HashSet(buildings)
        val newPerformanceMap = performanceMap.toMutableMap()
        for (building in buildingsToAdd) {
            state = state.addBuildingTo(building, newMap, newBuildings, newPerformanceMap, newFreeCells) ?: return null
        }
        return state

    }

    private fun addBuildingTo(
        building: Building,
        newMap: MutableMap<Cell, Building>,
        newBuildings: MutableSet<Building>,
        newPerformanceMap: MutableMap<Cell, Double>,
        newFreeCells: HashSet<Cell>
    ): State? {
        if (building.place.cells.any { it.x >= size.x || it.y >= size.y }) {
            return null
        }
        if (building.place.cells.any { it.x < 0 || it.y < 0 }) {
            return null
        }

        if (building.place.cells.intersect(map.keys).isNotEmpty()) {
            return null
        }

        if (!isLiquidsValid(building)) {
            return null
        }

        for (cell in building.place.cells) {
            newMap[cell] = building
        }

        newBuildings.add(building)

        if (building.type == BuildingType.BEACON) {
            for (x in -5..5)
                for (y in -5..5)
                    newPerformanceMap[Cell(building.place.start.x + x, building.place.start.y + y)] =
                        newPerformanceMap.getOrDefault(
                            Cell(building.place.start.x + x, building.place.start.y + y),
                            0.0
                        ) + (building as Beacon).speed()
        }

        newFreeCells.removeAll(
            Utils.cellsForBuilding(
                Cell(building.place.start.x - 2, building.place.start.y - 2),
                BuildingType.BEACON.size + building.type.size - 1
            )
        )
        //        if(newFreeCells != oldFreeCells(size, newBuildings)) {
        //            throw IllegalStateException("Free cells are not equal")
        //        }


        val newEmptyCountScore = recalculateEmptyCountScore(building, map, newMap, emptyCountScore, size)

        val state = State(newBuildings, newMap, size, newPerformanceMap, newFreeCells, newEmptyCountScore)
        //        if (state.emptyScore.value != newEmptyCountScore) {
        //            throw IllegalStateException("Empty score is not equal")
        //        }

        return state
    }


    private fun isLiquidsValid(building: Building): Boolean {
        if (building.type !in listOf(
                BuildingType.STORAGE_TANK,
                BuildingType.OIL_REFINERY,
                BuildingType.UNDERGROUND_PIPE,
                BuildingType.PIPE,
                BuildingType.CHEMICAL_PLANT,
            )
        ) {
            return true
        }
        if (!Utils.checkLiquids) {
            return true
        }
        val liquidBuilding = building as LiquidConnectionsBuilding
        val connections = liquidBuilding.getLiquidConnections()
        for (connection in connections) {
            val other = map[connection.to]
            if (other != null && other is LiquidConnectionsBuilding &&
                other.getLiquidConnections().any { it.isConnectionWith(connection) && it.liquid != connection.liquid }
            ) {
                return false
            }
        }

        return true
    }

    fun removeBuilding(building: Building): State {
        val newMap = map.toMutableMap()
        for (cell in building.place.cells) {
            newMap.remove(cell)
        }

        val newBuildings: MutableSet<Building> = HashSet(buildings)
        newBuildings.remove(building)

        val newPerformanceMap = if (building.type != BuildingType.BEACON) performanceMap else {
            val newPerformanceMap = performanceMap.toMutableMap()
            for (x in -5..5)
                for (y in -5..5)
                    newPerformanceMap[Cell(building.place.start.x + x, building.place.start.y + y)] =
                        newPerformanceMap[Cell(
                            building.place.start.x + x,
                            building.place.start.y + y
                        )]!! - (building as Beacon).speed()
            newPerformanceMap
        }

        val newFreeCells = HashSet(freeCells)
        newFreeCells.addAll(
            Utils.cellsForBuilding(
                Cell(building.place.start.x - 2, building.place.start.y - 2),
                BuildingType.BEACON.size + building.type.size - 1
            )
        )
        newBuildings.filter {
            it.place.start.maxDistanceTo(building.place.start) <= max(
                it.type.size,
                building.type.size
            ) + 1
        }
            .forEach {
                newFreeCells.removeAll(
                    Utils.cellsForBuilding(
                        Cell(it.place.start.x - 2, it.place.start.y - 2),
                        BuildingType.BEACON.size + it.type.size - 1
                    )
                )
            }
//        if(newFreeCells != oldFreeCells(size, newBuildings)) {
//            throw IllegalStateException("Free cells are not equal")
//        }
        val newEmptyCountScore = recalculateEmptyCountScore(building, map, newMap, emptyCountScore, size)


        val state = State(newBuildings, newMap, size, newPerformanceMap, newFreeCells, newEmptyCountScore)
//        if (state.emptyScore.value != newEmptyCountScore) {
//            throw IllegalStateException("Empty score is not equal")
//        }
        return state
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as State

        if (size != other.size) return false
        if (buildings != other.buildings) return false
//        if (map != other.map) return false

        return true
    }

    override fun hashCode(): Int {
        var result = buildings.hashCode()
//        result = 31 * result + map.hashCode()
        result = 31 * result + size.hashCode()
        return result
    }


    companion object {
        var count = 0

        fun allCells(size: Cell): Set<Cell> {
            val cells = HashSet<Cell>()
            for (x in 0 until size.x - 2) {
                for (y in 0 until size.y - 2) {
                    cells.add(Cell(x, y))
                }
            }
            return cells
        }

        fun oldFreeCells(size: Cell, buildings: Set<Building>): Set<Cell> {
            val cells = HashSet<Cell>()
            for (x in 0 until size.x - 2) {
                for (y in 0 until size.y - 2) {
                    cells.add(Cell(x, y))
                }
            }
            buildings.forEach {
                cells.removeAll(
                    Utils.cellsForBuilding(
                        Cell(it.place.start.x - 2, it.place.start.y - 2),
                        BuildingType.BEACON.size + it.type.size - 1
                    )
                )
            }
            return cells
        }

        private fun recalculateEmptyCountScore(
            building: Building,
            oldMap: Map<Cell, Building>,
            newMap: MutableMap<Cell, Building>,
            emptyCountScore: Int,
            size: Cell
        ): Int {
            var result = emptyCountScore
            val oldEmptyMap: MutableMap<Cell, Int> = mutableMapOf()
            val newEmptyMap: MutableMap<Cell, Int> = mutableMapOf()
            val startX = (building.place.start.x - 2).coerceAtLeast(0)
            val endX = (building.place.start.x + building.type.size + 2).coerceAtMost(size.x)
            val startY = (building.place.start.y - 2).coerceAtLeast(0)
            val endY = (building.place.start.y + building.type.size + 2).coerceAtMost(size.y)
            for (x in startX until endX) {
                for (y in startY until endY) {
                    val cell = Cell(x, y)
                    if (oldMap[cell] == null) {
                        var current = 0
                        if (oldMap[cell.right()] == null) {
                            current++
                        }
                        if (oldMap[cell.down()] == null) {
                            current++
                        }
                        if (oldMap[cell.left()] == null) {
                            current++
                        }
                        if (oldMap[cell.up()] == null) {
                            current++
                        }
                        if (current != 0) {
                            oldEmptyMap[cell] = current
                        }
                    }

                    if (newMap[cell] == null) {
                        var current = 0
                        if (newMap[cell.right()] == null) {
                            current++
                        }
                        if (newMap[cell.down()] == null) {
                            current++
                        }
                        if (newMap[cell.left()] == null) {
                            current++
                        }
                        if (newMap[cell.up()] == null) {
                            current++
                        }
                        if (current != 0) {
                            newEmptyMap[cell] = current
                        }
                    }
                }
            }
            for (x in startX until endX) {
                for (y in startY until endY) {
                    val cell = Cell(x, y)
                    if (oldEmptyMap[cell] != null) {
                        if (oldEmptyMap[cell.right()] != null)
                            result -= oldEmptyMap[cell]!! * oldEmptyMap[cell.right()]!!
                        if (oldEmptyMap[cell.down()] != null)
                            result -= oldEmptyMap[cell]!! * oldEmptyMap[cell.down()]!!
                    }
                    if (newEmptyMap[cell] != null) {
                        if (newEmptyMap[cell.right()] != null)
                            result += newEmptyMap[cell]!! * newEmptyMap[cell.right()]!!
                        if (newEmptyMap[cell.down()] != null)
                            result += newEmptyMap[cell]!! * newEmptyMap[cell.down()]!!
                    }
                }
            }

            return result
        }
    }
}

