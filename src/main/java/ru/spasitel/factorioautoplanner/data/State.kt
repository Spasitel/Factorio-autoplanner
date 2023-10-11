package ru.spasitel.factorioautoplanner.data

import kotlin.math.abs


data class State(
    var buildings: Set<Building>,
    var map: Map<Sell, Building>,
    var size: Sell
) {

    val area: Lazy<Int> = lazy {

        (size.x - 2) * (size.y - 2) - freeSells.value.size
    }

    val freeSells: Lazy<HashSet<Sell>> = lazy {
        var sells = HashSet<Sell>();
        for (x in 0 until size.x - 2) {
            for (y in 0 until size.y - 2) {
                sells.add(Sell.get(x, y))
            }
        }
        buildings.forEach {
            it.place.sells.forEach { sell ->
                sells.removeAll(Utils.sellsForBuilding(Sell.get(sell.x - 2, sell.y - 2), BuildingType.BEACON.size))
            }
        }
        sells
    }

    val production: Lazy<Double> = lazy {
        var score = 0.0
        for (b in buildings) {
            if (b.type == BuildingType.SMELTER) {
                score += 0.8
            }
            if (b.type == BuildingType.BEACON) {
                for (affected in buildings) {

                    if (affected.type != BuildingType.SMELTER) continue
                    if (abs(b.place.start.x - affected.place.start.x) < 6
                        && abs(b.place.start.y - affected.place.start.y) < 6
                    ) {
                        score += 0.5
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

        for (x in 0 until size.x) {
            for (y in 0 until size.y) {

                when (map[Sell.get(x, y)]?.type) {
                    BuildingType.SMELTER -> str.append('S')
                    BuildingType.BEACON -> str.append('B')
                    BuildingType.INSERTER -> str.append('>')
                    BuildingType.INPUT_CHEST -> str.append('I')
                    BuildingType.OUTPUT_CHEST -> str.append('O')
                    null -> str.append('.')
                    else -> str.append('E')
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
        if (building.place.sells.intersect(map.keys).isNotEmpty()) {
            return null
        }

        val newMap = map.toMutableMap()
        for (sell in building.place.sells) {
            newMap[sell] = building
        }

        val newBuildings: MutableSet<Building> = HashSet(buildings)
        newBuildings.add(building)
        return State(newBuildings, newMap, size)
    }

}