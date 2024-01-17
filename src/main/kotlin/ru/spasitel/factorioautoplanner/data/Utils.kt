package ru.spasitel.factorioautoplanner.data

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.building.*
import ru.spasitel.factorioautoplanner.formatter.Formatter
import ru.spasitel.factorioautoplanner.simple.InsertersPlaces

object Utils {
    private val logger = KotlinLogging.logger {}

    var checkLiquids = false

    const val START_JSON =
        "{\"blueprint\":{\"icons\":[{\"signal\":{\"type\":\"item\",\"name\":\"electric-furnace\"},\"index\":1}],\"entities\":["
    const val END_JSON = "],\"item\":\"blueprint\",\"version\":281479273775104}}"
    fun cellsForBuilding(start: Cell, size: Int): Set<Cell> {
        val cells = HashSet<Cell>()
        cells.add(start)
        for (x in 0 until size) {
            for (y in 0 until size) {
                cells.add(Cell(start.x + x, start.y + y))
            }
        }
        return cells
    }

    fun getBuilding(
        start: Cell,
        type: BuildingType,
        direction: Int? = null,
        liquid: String? = null,
        recipe: String? = null,
        kind: String = "stack-inserter",
        items: MutableSet<String> = mutableSetOf()
    ): Building {
        val place = Place(cellsForBuilding(start, type.size), start)

        return when (type) {
            BuildingType.BEACON -> Beacon(place)
            BuildingType.SMELTER -> Smelter(place)
            BuildingType.PROVIDER_CHEST -> ProviderChest(place, items = items)
            BuildingType.REQUEST_CHEST -> RequestChest(place)
            BuildingType.EMPTY -> EmptySpace(place, 1)
            BuildingType.EMPTY2 -> EmptySpace(place, 2)
            BuildingType.EMPTY3 -> EmptySpace(place, 3)
            BuildingType.EMPTY4 -> EmptySpace(place, 4)
            BuildingType.INSERTER -> Inserter(place, direction!!, kind)
            BuildingType.PUMP -> Pump(placeForPump(start, direction!!), direction)
            BuildingType.STORAGE_TANK -> StorageTank(place, liquid!!, direction!!)
            BuildingType.OIL_REFINERY -> OilRefinery(place, direction!!)
            BuildingType.PIPE -> Pipe(place, liquid!!)
            BuildingType.UNDERGROUND_PIPE -> UndergroundPipe(place, liquid!!, direction!!)
            BuildingType.CHEMICAL_PLANT -> ChemicalPlant(place, direction!!, recipe!!)
            BuildingType.ASSEMBLER -> Assembler(place, direction, recipe!!)
            BuildingType.LAB -> Lab(place)
            BuildingType.ROCKET_SILO -> RocketSilo(place, direction!!)
            BuildingType.STEEL_CHEST -> SteelChest(place)
            BuildingType.ROBOPORT -> Roboport(place)
            BuildingType.TRAIN_STOP -> TrainStop(place, recipe!!, direction!!)

            else -> throw IllegalArgumentException("Unknown building type $type")
        }
    }

    private fun placeForPump(start: Cell, direction: Int): Place {
        return when (direction) {
            0 -> Place(setOf(start, start.down()), start)
            2 -> Place(setOf(start, start.right()), start)
            4 -> Place(setOf(start, start.down()), start)
            6 -> Place(setOf(start, start.right()), start)
            else -> throw IllegalArgumentException("Unknown direction $direction")
        }
    }


    fun chestsPositions(building: Building): Set<Triple<Cell, Cell, Int>> {
        val cells = HashSet<Triple<Cell, Cell, Int>>()
        for (position in InsertersPlaces.entries) {
            cells.add(
                Triple(
                    Cell(building.place.start.x + position.iX, building.place.start.y + position.iY),
                    Cell(building.place.start.x + position.cX, building.place.start.y + position.cY),
                    position.direction
                )
            )
        }
        return cells
    }


    @JvmStatic
    fun printBest(best: State) {
        logger.info { best }
        val message = convertToJson(best)
        logger.info { message }
    }

    fun convertToJson(best: State): String? {
        var bCount = 1
        var json = StringBuilder(START_JSON)
        for (b in best.buildings) {
            if (b.type == BuildingType.EMPTY || b.type == BuildingType.EMPTY2 || b.type == BuildingType.EMPTY3 || b.type == BuildingType.EMPTY4) continue
            json.append(b.toJson(bCount))
            bCount++
        }
        json = StringBuilder(json.substring(0, json.length - 1))
        json.append(END_JSON)
        val message = Formatter.encode(json.toString())
        return message
    }

    fun isBetween(x: Int, y: Int, chestField: Pair<Cell, Cell>, shift: Int = 0): Boolean {
        return x in chestField.first.x - shift..chestField.second.x + shift && y in chestField.first.y - shift..chestField.second.y + shift
    }

    fun isBetween(start: Cell, electricField: Pair<Cell, Cell>, shift: Int = 0): Boolean {
        return isBetween(start.x, start.y, electricField, shift)
    }
}