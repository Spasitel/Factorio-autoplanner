package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.building.*
import ru.spasitel.factorioautoplanner.simple.InsertersPlaces

object Utils {
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
            BuildingType.EMPTY -> EmptySpace(place)

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
}