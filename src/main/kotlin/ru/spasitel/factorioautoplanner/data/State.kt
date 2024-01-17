package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.building.Building
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import ru.spasitel.factorioautoplanner.data.building.LiquidConnectionsBuilding
import kotlin.math.max


data class State(
    val buildings: Set<Building>,
    val map: Map<Cell, Building>,
    val size: Cell,
    val performanceMap: Map<Cell, Int> = HashMap(),
    val freeCells: Set<Cell> = allCells(size)
) {

    private val number: Int = count++

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

        val newMap = map.toMutableMap()
        for (cell in building.place.cells) {
            newMap[cell] = building
        }

        val newBuildings: MutableSet<Building> = HashSet(buildings)
        newBuildings.add(building)


        val newPerformanceMap = if (building.type != BuildingType.BEACON) performanceMap else {
            val newPerformanceMap = performanceMap.toMutableMap()
            for (x in -5..5)
                for (y in -5..5)
                    newPerformanceMap[Cell(building.place.start.x + x, building.place.start.y + y)] =
                        newPerformanceMap.getOrDefault(
                            Cell(building.place.start.x + x, building.place.start.y + y),
                            0
                        ) + 1
            newPerformanceMap
        }

        val newFreeCells = HashSet(freeCells)
        newFreeCells.removeAll(
            Utils.cellsForBuilding(
                Cell(building.place.start.x - 2, building.place.start.y - 2),
                BuildingType.BEACON.size + building.type.size - 1
            )
        )
//        if(newFreeCells != oldFreeCells(size, newBuildings)) {
//            throw IllegalStateException("Free cells are not equal")
//        }
        return State(newBuildings, newMap, size, newPerformanceMap, newFreeCells)
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
                        newPerformanceMap[Cell(building.place.start.x + x, building.place.start.y + y)]!! - 1
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

        return State(newBuildings, newMap, size, newPerformanceMap, newFreeCells)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as State

        if (buildings != other.buildings) return false
        if (map != other.map) return false
        if (size != other.size) return false

        return true
    }

    override fun hashCode(): Int {
        return number
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
    }
}