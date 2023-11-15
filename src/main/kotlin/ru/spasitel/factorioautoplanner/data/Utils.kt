package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.building.*
import ru.spasitel.factorioautoplanner.formatter.Formatter
import ru.spasitel.factorioautoplanner.simple.InsertersPlaces

object Utils {

    const val START_JSON =
        "{\"blueprint\":{\"icons\":[{\"signal\":{\"type\":\"item\",\"name\":\"electric-furnace\"},\"index\":1}],\"entities\":["
    const val END_JSON = "],\"item\":\"blueprint\",\"version\":281479273775104}}"
    fun sellsForBuilding(start: Sell, size: Int): Set<Sell> {
        val sells = HashSet<Sell>()
        sells.add(start)
        for (x in 0 until size) {
            for (y in 0 until size) {
                sells.add(Sell(start.x + x, start.y + y))
            }
        }
        return sells
    }

    fun getBuilding(
        start: Sell,
        type: BuildingType
    ): Building {
        val place = Place(sellsForBuilding(start, type.size), start)

        return when (type) {
            BuildingType.BEACON -> Beacon(place)
            BuildingType.SMELTER -> Smelter(place)
            BuildingType.PROVIDER_CHEST -> Chest(place, true)
            BuildingType.REQUEST_CHEST -> Chest(place, false)
            BuildingType.EMPTY -> EmptySpace(place, 1)
            BuildingType.EMPTY2 -> EmptySpace(place, 2)
            BuildingType.EMPTY3 -> EmptySpace(place, 3)
            BuildingType.EMPTY4 -> EmptySpace(place, 4)

            else -> throw IllegalArgumentException("Unknown building type")
        }
    }


    fun chestsPositions(building: Building): Set<Triple<Sell, Sell, Int>> {
        val sells = HashSet<Triple<Sell, Sell, Int>>()
        for (position in InsertersPlaces.values()) {
            sells.add(
                Triple(
                    Sell(building.place.start.x + position.iX, building.place.start.y + position.iY),
                    Sell(building.place.start.x + position.cX, building.place.start.y + position.cY),
                    position.direction
                )
            )
        }
        return sells
    }


    @JvmStatic
    fun printBest(best: State) {
        println(best)
        var bCount = 1
        var json = StringBuilder(START_JSON)
        for (b in best.buildings) {
            if (b.type == BuildingType.EMPTY) continue
            json.append(b.toJson(bCount))
            bCount++
        }
        json = StringBuilder(json.substring(0, json.length - 1))
        json.append(END_JSON)
        println(Formatter.encode(json.toString()))
    }

    fun isBetween(x: Int, y: Int, chestField: Pair<Sell, Sell>): Boolean {
        return x in chestField.first.x..chestField.second.x && y in chestField.first.y..chestField.second.y
    }

    fun isBetween(sell: Sell, start: Sell, end: Sell): Boolean {
        return sell.x in start.x..end.x && sell.y in start.y..end.y
    }
}