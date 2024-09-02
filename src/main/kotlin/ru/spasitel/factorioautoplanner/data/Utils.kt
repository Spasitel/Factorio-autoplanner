package ru.spasitel.factorioautoplanner.data

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.building.*
import ru.spasitel.factorioautoplanner.formatter.Formatter
import ru.spasitel.factorioautoplanner.planner.GlobalPlanner
import ru.spasitel.factorioautoplanner.simple.InsertersPlaces
import kotlin.math.abs

object Utils {
    private val logger = KotlinLogging.logger {}

    var checkLiquids = false

    const val START_JSON =
        "{\"blueprint\":{\"icons\":[{\"signal\":{\"type\":\"virtual\",\"name\":\"signal-%d\"},\"index\":1},{\"signal\":{\"type\":\"virtual\",\"name\":\"signal-%d\"},\"index\":2},{\"signal\":{\"type\":\"virtual\",\"name\":\"signal-%d\"},\"index\":3},{\"signal\":{\"type\":\"virtual\",\"name\":\"signal-%d\"},\"index\":4}],\"entities\":["
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

    fun speedModule(moduleLvl: Int): String = when (moduleLvl) {
        1 -> "speed-module"
        2 -> "speed-module-2"
        3 -> "speed-module-3"
        else -> throw IllegalArgumentException("Unknown module level $moduleLvl")
    }

    fun getBuilding(
        start: Cell,
        type: BuildingType,
        direction: Int? = null,
        liquid: String? = null,
        recipe: String? = null,
        kind: String = "fast-inserter",
        items: MutableSet<String> = mutableSetOf(),
        moduleLvl: Int = 3
    ): Building {
        val place = Place(cellsForBuilding(start, type.size), start)


        return when (type) {
            BuildingType.BEACON -> Beacon(place, moduleLvl)
            BuildingType.SMELTER -> Smelter(place, recipe!!)
            BuildingType.PROVIDER_CHEST -> ProviderChest(place, items = items)
            BuildingType.REQUEST_CHEST -> {
                val rItems = HashMap<String, Int>()
                if (GlobalPlanner.isSmelter) {
                    rItems["${GlobalPlanner.smelterOre}-ore"] = 300
                }
                RequestChest(place, rItems)
            }

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
            BuildingType.CHEMICAL_PLANT -> ChemicalPlant(place, direction!!, recipe!!, moduleLvl)
            BuildingType.ASSEMBLER -> Assembler(place, direction, recipe!!)
            BuildingType.LAB -> Lab(place)
            BuildingType.ROCKET_SILO -> RocketSilo(place, direction!!)
            BuildingType.STEEL_CHEST -> SteelChest(place)
            BuildingType.ROBOPORT -> Roboport(place)
            BuildingType.TRAIN_STOP -> TrainStop(place, recipe!!, direction!!)
            BuildingType.ELECTRIC_MINING_DRILL -> ElectricMiningDrill(place)

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
        var json = StringBuilder(
            String.format(
                START_JSON,
                best.hashCode().div(1000).mod(10),
                best.hashCode().div(100).mod(10),
                best.hashCode().div(10).mod(10),
                best.hashCode().mod(10),
            )
        )
        for (b in best.buildings) {
            if (b.type == BuildingType.EMPTY || b.type == BuildingType.EMPTY2 || b.type == BuildingType.EMPTY3 || b.type == BuildingType.EMPTY4) continue
            json.append(b.toJson())
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

    fun isBeaconAffect(
        beacon: Building,
        to: Building
    ): Boolean {
        if (beacon.type != BuildingType.BEACON) {
            throw IllegalArgumentException("First building should be beacon")
        }
        if (to.type.size == 3) {
            return beacon.place.start.maxDistanceTo(to.place.start) < 6
        }
        val distanceX =
            abs(beacon.place.start.x + beacon.type.size / 2.0 - to.place.start.x - to.type.size / 2.0) - to.type.size / 2.0
        val distanceY =
            abs(beacon.place.start.y + beacon.type.size / 2.0 - to.place.start.y - to.type.size / 2.0) - to.type.size / 2.0
        return distanceX < 4 && distanceY < 4
    }
}