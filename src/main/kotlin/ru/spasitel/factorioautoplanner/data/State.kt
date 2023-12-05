package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.building.Building
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import kotlin.math.abs


data class State(
    var buildings: Set<Building>,
    var map: Map<Cell, Building>,
    var size: Cell
) {

    val area: Lazy<Int> = lazy {

        (size.x - 2) * (size.y - 2) - freeCells.value.size
    }

    val freeCells: Lazy<HashSet<Cell>> = lazy {
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
        cells
    }

    val production: Lazy<Double> = lazy {
        var score = 0.0
        for (b in buildings) {
            if (b.type == BuildingType.SMELTER) {
                score += 4
            }
            if (b.type == BuildingType.BEACON) {
                for (affected in buildings) {

                    if (affected.type != BuildingType.SMELTER) continue
                    if (abs(b.place.start.x - affected.place.start.x) < 6
                        && abs(b.place.start.y - affected.place.start.y) < 6
                    ) {
                        score += 1
                    }
                }
            }
        }
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


        val newMap = map.toMutableMap()
        for (cell in building.place.cells) {
            newMap[cell] = building
        }

        val newBuildings: MutableSet<Building> = HashSet(buildings)
        newBuildings.add(building)
        return State(newBuildings, newMap, size)
    }

    fun removeBuilding(building: Building): State {
        val newMap = map.toMutableMap()
        for (cell in building.place.cells) {
            newMap.remove(cell)
        }

        val newBuildings: MutableSet<Building> = HashSet(buildings)
        newBuildings.remove(building)
        return State(newBuildings, newMap, size)
    }

}