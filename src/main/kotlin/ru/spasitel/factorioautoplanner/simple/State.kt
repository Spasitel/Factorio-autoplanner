package ru.spasitel.factorioautoplanner.simple

import ru.spasitel.factorioautoplanner.simple.building.Building
import ru.spasitel.factorioautoplanner.simple.building.Smelter
import ru.spasitel.factorioautoplanner.simple.building.Type

class State(var buildings: Set<Building>, var score: Double, var area: Int, var map: Array<Array<Building?>>) {
    private fun checkState() {
        if (Main.debug) {
            var checked = 0
            for (b in buildings) {
                for (x in b.x until b.x + b.size) for (y in b.y until b.y + b.size) {
                    if (b != map!![x]!![y]) throw RuntimeException()
                    checked++
                }
            }
            for (x in 0 until Main.SIZE) for (y in 0 until Main.SIZE) if (map!![x]!![y] != null) checked--
            if (checked != 0) throw RuntimeException()
        }
    }

    override fun toString(): String {
        val str = StringBuilder("=========================================================\n")
        var smelter = 0
        for (b in buildings) {
            if (b.type == Type.SMELTER) smelter++
        }
        str.append("Score: ").append(score).append(" Area: ").append(area).append(" Efficiency: ").append(score / area)
            .append(" Buildings (s/b): ").append(smelter).append("/").append(buildings.size - smelter).append("\n")
        val view = Array(18) { CharArray(18) }
        for (i in 0 until Main.SIZE) for (k in 0 until Main.SIZE) view[i][k] = '.'
        for (b in buildings) {
            val ch = b.symbol
            for (i in 0 until b.size) for (k in 0 until b.size) view[b.x + i][b.y + k] =
                if (i == 0 && k == 0) ch.uppercaseChar() else ch
        }
        for (i in 0 until Main.SIZE) {
            for (k in 0 until Main.SIZE) {
                str.append(view[k][i])
            }
            str.append("\n")
        }
        str.append(
            "State{" +
                    "buildings=" + buildings +
                    ", score=" + score +
                    ", area=" + area +
                    '}'
        )
        return str.toString()
    }

    fun addBuilding(building: Building): State? {
        val newMap = Main.copyOf(map)

        // Проверить что не пересекается
        for (x in building.x until building.x + building.size) {
            for (y in building.y until building.y + building.size) {
                if (map[x]?.get(y) != null) return null
                newMap!![x]!![y] = building
            }
        }
        val newBuildings: MutableSet<Building> = HashSet<Building>(buildings)
        newBuildings.add(building)
        return State(newBuildings, score, area, newMap)
    }

    fun replaceSmelter(smelter: Smelter): State? {
        val old = map[smelter.x]!![smelter.y] as Smelter
        val newMap = Main.copyOf(map)
        for (x in smelter.x until smelter.x + smelter.size) {
            for (y in smelter.y until smelter.y + smelter.size) {
                if (map[x]?.get(y) !== old) return null
                newMap!![x]!![y] = smelter
            }
        }
        val newBuildings: Set<Building> = buildings.map { b: Building -> if (b == old) smelter else b }.toSet()
        return State(newBuildings, score, area, newMap)
    }

    init {
        checkState()
    }
}