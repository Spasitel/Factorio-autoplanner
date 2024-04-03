package ru.spasitel.factorioautoplanner.formatter

import com.google.gson.Gson
import io.github.oshai.kotlinlogging.KotlinLogging
import ru.spasitel.factorioautoplanner.data.*
import ru.spasitel.factorioautoplanner.data.auto.Blueprint
import ru.spasitel.factorioautoplanner.data.auto.BlueprintDTO
import ru.spasitel.factorioautoplanner.data.auto.Entity
import ru.spasitel.factorioautoplanner.data.auto.Position
import ru.spasitel.factorioautoplanner.data.building.*
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ROBOPORT_DISTANCE = 25

class BluePrintFieldExtractor {
    private val logger = KotlinLogging.logger {}

    fun transformBlueprintToField(blueprintDTO: BlueprintDTO): Field {

        val blueprint = normalizeBlueprint(blueprintDTO)

        val (roboportsField, chestField) = getRoboportsAndChests(blueprint)

//        val electricField = calculateElectricField(blueprint)

        var state = transformBuildingsToEmpty(blueprint)
        calculateChests(blueprint, state)
        calculateLiquids(blueprint, state)

        state = fixRecipes(state)
        return Field(
            state, roboportsField, chestField,
            Pair(
                chestField.first.move(Direction.UP, 4).move(Direction.LEFT, 4),
                chestField.second.move(Direction.DOWN, 4).move(Direction.RIGHT, 4)
            )
        )
    }

    private fun fixRecipes(state: State): State {
        var result = state
        state.buildings.filterIsInstance<Assembler>().filter { it.recipe == "electronic-circuit" }.forEach {
            val inserter =
                state.buildings.find { b -> b.type == BuildingType.INSERTER && (b as Inserter).from() in it.place.cells } as Inserter
            if (state.map[inserter.to()] is Assembler) {
                result = result.removeBuilding(it)
                result = result.addBuilding(it.copy(recipe = "electronic-circuit#blue"))!!
            }
        }

        state.buildings.filterIsInstance<Assembler>().filter { it.recipe == "copper-cable" }.forEach {
            val inserter =
                state.buildings.find { b -> b.type == BuildingType.INSERTER && (b as Inserter).from() in it.place.cells } as Inserter
            if (state.map[inserter.to()] is Assembler) {
                var add = "#green"
                if ((result.map[inserter.to()] as Assembler).recipe == "electronic-circuit#blue") {
                    add = "#blue"
                }
                result = result.removeBuilding(it)
                result = result.addBuilding(it.copy(recipe = "copper-cable$add"))!!
            }
        }


        return result
    }

    private fun calculateLiquids(blueprint: BlueprintDTO, state: State) {
        val source = mapOf(
            "petroleum-gas" to "light-oil-cracking",
            "lubricant" to "lubricant",
            "sulfuric-acid" to "sulfuric-acid",
        )

        source.forEach { (liquid, recipe) ->
            val plant =
                state.buildings.find { it.type == BuildingType.CHEMICAL_PLANT && (it as ChemicalPlant).recipe == recipe } as ChemicalPlant
            val start =
                plant.getLiquidConnections().map { it.to }
                    .find { state.map[it] != null && state.map[it]!!.type == BuildingType.PIPE }!!

            val todo = mutableSetOf(state.map[start] as LiquidBuilding)

            while (todo.isNotEmpty()) {
                var building = todo.first()
                todo.remove(building)
                if (building.liquid == liquid) {
                    continue
                }
                if (building.liquid != "empty") {
                    throw RuntimeException("Can't calculate liquid for $building")
                }
                building.liquid = liquid

                if (building is UndergroundPipe) {
                    var cell = building.place.start
                    val back = Direction.fromInt(building.direction).turnBack()
                    var distance = 0
                    while (state.map[cell] == null ||
                        state.map[cell]!!.type != BuildingType.UNDERGROUND_PIPE ||
                        (state.map[cell]!! as UndergroundPipe).direction != back.direction
                    ) {

                        cell = cell.move(back)
                        distance++
                        if (distance > 10) {
                            throw RuntimeException("Can't find connection for $building")
                        }
                        if (state.map[cell] != null &&
                            state.map[cell]!!.type == BuildingType.UNDERGROUND_PIPE &&
                            (state.map[cell]!! as UndergroundPipe).direction == building.direction
                        ) {
                            throw RuntimeException("Wrong connection for $building")
                        }
                    }
                    building = state.map[cell] as LiquidBuilding
                    building.liquid = liquid
                }

                building.getLiquidConnections().forEach { connection ->
                    if (state.map[connection.to] != null) {
                        val next = state.map[connection.to]!!
                        if (next is LiquidBuilding && next.getLiquidConnections()
                                .find { it.to == connection.from } != null
                        ) {
                            todo.add(next)
                        }
                    }
                }
            }
            var newState = State(emptySet(), emptyMap(), state.size)
            state.buildings.forEach {
                if (it is LiquidBuilding && it.liquid == liquid) {
                    newState = newState.addBuilding(it)!!
                }
            }

            logger.info { "=======$liquid========" }

            logger.info { Utils.convertToJson(newState) }

        }
    }

    private fun calculateChests(blueprint: BlueprintDTO, state: State): Map<String, Set<ProviderChest>> {
        val providers = state.buildings.filter { it.type == BuildingType.PROVIDER_CHEST }
        val chests = HashMap<String, MutableSet<ProviderChest>>()
        val stops = HashMap<Cell, String>()
        blueprint.blueprint.entities.filter { it.name == "train-stop" }.forEach {
            val name = it.station!!
            if (name.contains("water") || name.contains("oil")) {
                return@forEach
            }
            stops[Cell(it.position.x.roundToInt(), it.position.y.roundToInt())] = name
        }

        providers.forEach { provider ->
            val start = provider.place.start
            val inserterO = state.buildings.find { it.type == BuildingType.INSERTER && (it as Inserter).to() == start }
            val inserter = inserterO as Inserter
            var building = state.map[inserter.from()]
            if (building != null && building.type == BuildingType.STEEL_CHEST) {
                val inserter_2 =
                    state.buildings.find { it.type == BuildingType.INSERTER && (it as Inserter).to() == building!!.place.start } as Inserter
                building = state.map[inserter_2.from()]
            }
            if (building == null || building.type == BuildingType.EMPTY) {
                val name = stops.keys.minByOrNull { s -> abs(s.x - start.x) + abs(s.y - start.y) }.let { s ->
                    stops[s]
                }
                if (name != null) {
                    chests.getOrPut(name) { HashSet() }.add(provider as ProviderChest)
                } else {
                    logger.info { "Can't find stop for chest $start" }
                }
                when (name) {
                    "copper unload" -> (provider as ProviderChest).items.add("copper-plate")
                    "iron unload" -> (provider as ProviderChest).items.add("iron-plate")
                    "steel unload" -> (provider as ProviderChest).items.add("steel-plate")
                    "stone unload" -> (provider as ProviderChest).items.add("stone")
                    "coal unload" -> (provider as ProviderChest).items.add("coal")
                    else -> logger.info { "Unknown chest $name" }
                }

            } else if (building.type == BuildingType.ROCKET_SILO) {
                (provider as ProviderChest).items.add("space-science-pack")
            } else if (building.type == BuildingType.ASSEMBLER) {
                (provider as ProviderChest).items.add((building as Assembler).recipe)
            } else if (building.type == BuildingType.CHEMICAL_PLANT) {
                if ((building as ChemicalPlant).recipe == "sulfur") {
                    (provider as ProviderChest).items.add("sulfur")
                } else if (building.recipe == "plastic-bar") {
                    (provider as ProviderChest).items.add("plastic-bar")
                } else if (building.recipe == "battery") {
                    (provider as ProviderChest).items.add("battery")
                } else {
                    logger.info { "Unknown recipe ${building.recipe}" }
                }
            } else if (building.type == BuildingType.REQUEST_CHEST) {
                (provider as ProviderChest).items.add("empty")
            } else if (building.type == BuildingType.SMELTER) {
                (provider as ProviderChest).items.add("stone-brick")
            } else {
                logger.info { "Can't find items for chest $start" }
            }

        }

        return chests
    }

    fun normalizeBlueprint(blueprintDTO: BlueprintDTO): BlueprintDTO {
        if (blueprintDTO.blueprint.entities.minOf { it.position.x }.roundToInt() in 0..10 &&
            blueprintDTO.blueprint.entities.minOf { it.position.y }.roundToInt() in 0..10
        ) {
            return blueprintDTO
        }

        val startX = blueprintDTO.blueprint.entities.minOf { it.position.x }.roundToInt() - 4
        val startY = blueprintDTO.blueprint.entities.minOf { it.position.y }.roundToInt() - 4

        val entities = blueprintDTO.blueprint.entities.map {
            Entity(
                it.connections,
                it.control_behavior,
                it.direction,
                it.entity_number,
                it.name,
                it.neighbours,
                Position(it.position.x - startX, it.position.y - startY),
                it.recipe,
                it.station
            )
        }
        return BlueprintDTO(
            Blueprint(
                entities,
                blueprintDTO.blueprint.icons,
                blueprintDTO.blueprint.item,
                blueprintDTO.blueprint.version
            )
        )
    }

    private fun toPair(Entity: Entity): Pair<Double, Double> {
        return Pair(Entity.position.x, Entity.position.y)
    }

    private fun getRoboportsAndChests(blueprintDTO: BlueprintDTO): Pair<Pair<Cell, Cell>, Pair<Cell, Cell>> {
        val x =
            (blueprintDTO.blueprint.entities.maxOf { it.position.x } + blueprintDTO.blueprint.entities.minOf { it.position.x }) / 2
        val y =
            (blueprintDTO.blueprint.entities.maxOf { it.position.y } + blueprintDTO.blueprint.entities.minOf { it.position.y }) / 2
        val roboports = blueprintDTO.blueprint.entities.filter { it.name == "roboport" }
        val roboport = roboports
            .minWithOrNull(compareBy { abs(it.position.x - x) + abs(it.position.y - y) })!!
        val roboportSet = HashSet<Pair<Double, Double>>()
        roboportSet.add(toPair(roboport))
        var changed = true
        while (changed) {
            changed = false
            roboports.map { toPair(it) }.minus(roboportSet).filter { new ->
                roboportSet.find { abs(it.first - new.first) < ROBOPORT_DISTANCE * 2 && abs(it.second - new.second) < ROBOPORT_DISTANCE * 2 } != null
            }.forEach {
                roboportSet.add(it)
                changed = true
            }
        }

        val roboportsField = Pair(
            Cell(
                (roboportSet.minOf { it.first } - 2).roundToInt(),
                (roboportSet.minOf { it.second } - 2).roundToInt()
            ),
            Cell(
                (roboportSet.maxOf { it.first } - 2).roundToInt(),
                (roboportSet.maxOf { it.second } - 2).roundToInt()
            )
        )

        val chestField = Pair(
            Cell(
                (roboportsField.first.x - ROBOPORT_DISTANCE + 2),
                (roboportsField.first.y - ROBOPORT_DISTANCE + 2)
            ),
            Cell(
                (roboportsField.second.x + ROBOPORT_DISTANCE + 1),
                (roboportsField.second.y + ROBOPORT_DISTANCE + 1)
            )
        )

        return Pair(roboportsField, chestField)
    }

    private fun transformBuildingsToEmpty(blueprintDTO: BlueprintDTO): State {
        val size = Cell(
            blueprintDTO.blueprint.entities.maxOf { it.position.x }.roundToInt() + 6,
            blueprintDTO.blueprint.entities.maxOf { it.position.y }.roundToInt() + 6
        )
        var state = State(emptySet(), emptyMap(), size)
        val curved = curvedRails()
        for (entity in blueprintDTO.blueprint.entities.filter { it.name != "curved-rail" }) {
            val x = entity.position.x
            val y = entity.position.y
            when (entity.name) {
                "rail-signal",
                "small-lamp",
                "rail-chain-signal",
                "express-underground-belt",
                "logistic-chest-active-provider",
                "logistic-chest-storage",
                "filter-inserter",
                "medium-electric-pole" -> {
                    val start = Cell((x - 0.5).roundToInt(), (y - 0.5).roundToInt())
                    val addBuilding = state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY))
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "steel-chest" -> {
                    val start = Cell((x - 0.5).roundToInt(), (y - 0.5).roundToInt())
                    val addBuilding = state.addBuilding(Utils.getBuilding(start, BuildingType.STEEL_CHEST))
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }


                "big-electric-pole",
                "laser-turret",
                "substation" -> {
                    val start = Cell((x - 1).roundToInt(), (y - 1).roundToInt())
                    state = state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY2))!!

                }

                "radar" -> {
                    val start = Cell((x - 1.5).roundToInt(), (y - 1.5).roundToInt())
                    val addBuilding = state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY3))
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }


                "roboport" -> {
                    val start = Cell((x - 2).roundToInt(), (y - 2).roundToInt())
                    state = state.addBuilding(Utils.getBuilding(start, BuildingType.ROBOPORT))!!
                }

                "arithmetic-combinator",
                "decider-combinator" -> {
                    if (entity.direction == 0 || entity.direction == 4) {
                        val start = Cell((x - 0.5).roundToInt(), (y - 1).roundToInt())
                        state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.down(), BuildingType.EMPTY))?.let { state = it }
                    } else {
                        val start = Cell((x - 1).roundToInt(), (y - 0.5).roundToInt())
                        state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.right(), BuildingType.EMPTY))?.let { state = it }
                    }
                }

                "straight-rail" -> {
                    if (entity.direction.mod(2) == 0) {
                        val start = Cell((x - 1).roundToInt(), (y - 1).roundToInt())
                        state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.down(), BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.right(), BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.down().right(), BuildingType.EMPTY))
                            ?.let { state = it }

                    } else {
                        var start = Cell((x).roundToInt(), (y).roundToInt())
                        when (entity.direction) {
                            1 -> {
                                start = start.up()
                            }

                            5 -> {
                                start = start.left()
                            }

                            7 -> {
                                start = start.up().left()
                            }
                        }

                        val addBuilding = state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY))
                        if (addBuilding == null) {
//                            logger.info {"Can't add building $entity"}
                        } else {
                            state = addBuilding
                        }
                        state.addBuilding(Utils.getBuilding(start.left(), BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.right(), BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.down(), BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.up(), BuildingType.EMPTY))?.let { state = it }
                    }
                }

                "storage-tank" -> {
                    val start = Cell((x - 1.5).roundToInt(), (y - 1.5).roundToInt())
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.STORAGE_TANK,
                            direction = entity.direction,
                            liquid = "empty"
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "pipe" -> {
                    val start = Cell((x - 0.5).roundToInt(), (y - 0.5).roundToInt())
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.PIPE,
                            liquid = "empty"
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "pipe-to-ground" -> {
                    val start = Cell((x - 0.5).roundToInt(), (y - 0.5).roundToInt())
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.UNDERGROUND_PIPE,
                            direction = entity.direction,
                            liquid = "empty"
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "pump" -> {
                    val start = Cell((x - 0.5).toInt(), (y - 0.5).toInt())
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.PUMP,
                            direction = entity.direction
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "inserter",
                "stack-inserter",
                "long-handed-inserter",
                "fast-inserter" -> {
                    val start = Cell((x - 0.5).roundToInt(), (y - 0.5).roundToInt())
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.INSERTER,
                            direction = entity.direction,
                            kind = entity.name
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "logistic-chest-requester" -> {
                    val start = Cell((x - 0.5).roundToInt(), (y - 0.5).roundToInt())
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.REQUEST_CHEST
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "logistic-chest-passive-provider" -> {
                    val start = Cell((x - 0.5).roundToInt(), (y - 0.5).roundToInt())
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.PROVIDER_CHEST
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "oil-refinery" -> {
                    val start = Cell(
                        (x - BuildingType.OIL_REFINERY.size / 2.0).roundToInt(),
                        (y - BuildingType.OIL_REFINERY.size / 2.0).roundToInt()
                    )
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.OIL_REFINERY,
                            direction = entity.direction
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "chemical-plant" -> {
                    val start = Cell(
                        (x - BuildingType.CHEMICAL_PLANT.size / 2.0).roundToInt(),
                        (y - BuildingType.CHEMICAL_PLANT.size / 2.0).roundToInt()
                    )
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.CHEMICAL_PLANT,
                            direction = entity.direction,
                            recipe = entity.recipe!!
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "assembling-machine-3" -> {
                    val start = Cell(
                        (x - BuildingType.ASSEMBLER.size / 2.0).roundToInt(),
                        (y - BuildingType.ASSEMBLER.size / 2.0).roundToInt()
                    )
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.ASSEMBLER,
                            direction = entity.direction,
                            recipe = entity.recipe!!
                            //todo: add module
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "rocket-silo" -> {
                    val start = Cell(
                        (x - BuildingType.ROCKET_SILO.size / 2.0).roundToInt(),
                        (y - BuildingType.ROCKET_SILO.size / 2.0).roundToInt()
                    )
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.ROCKET_SILO,
                            direction = entity.direction
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "lab" -> {
                    val start = Cell((x - 1.5).roundToInt(), (y - 1.5).roundToInt())
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.LAB,
                            direction = entity.direction
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding
                    }
                }

                "beacon" -> {
                    val start = Cell((x - 1.5).roundToInt(), (y - 1.5).roundToInt())
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.BEACON
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding

                    }
                }

                "electric-furnace" -> {
                    val start = Cell(
                        (x - BuildingType.SMELTER.size / 2.0).roundToInt(),
                        (y - BuildingType.SMELTER.size / 2.0).roundToInt()
                    )
                    val addBuilding = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.SMELTER
                        )
                    )
                    if (addBuilding == null) {
                        logger.info { "Can't add building $entity" }
                    } else {
                        state = addBuilding

                    }
                }

                "train-stop" -> {
                    val start = Cell((x - 1).roundToInt(), (y - 1).roundToInt())
                    state = state.addBuilding(
                        Utils.getBuilding(
                            start,
                            BuildingType.TRAIN_STOP,
                            recipe = entity.station!!,
                            direction = entity.direction
                        )
                    )!!
                }

                else -> throw RuntimeException("Unknown entity ${entity.name}")

            }
        }
        for (entity in blueprintDTO.blueprint.entities.filter { it.name == "curved-rail" }) {
            val x = entity.position.x
            val y = entity.position.y

            val set = curved[entity.direction]!!
            for (cell in set) {
                state.addBuilding(
                    Utils.getBuilding(
                        Cell((x + cell.x).roundToInt(), (y + cell.y).roundToInt()),
                        BuildingType.EMPTY
                    )
                )?.let { state = it }
            }

        }
        return state
    }

    private fun curvedRails(): Map<Int, Set<Cell>> {
        val blueprint = Gson().fromJson(Formatter.decode(CURVED_RAILS), BlueprintDTO::class.java)
        val rails = blueprint.blueprint.entities.filter { it.name == "curved-rail" }.sortedBy { it.position.x }
        val chests =
            blueprint.blueprint.entities.filter { it.name == "logistic-chest-storage" }.sortedBy { it.position.x }
                .toMutableList()
        val result = HashMap<Int, Set<Cell>>()

        //10,16
        var odd = false

        rails.forEach { rail ->
            val xr = rail.position.x.roundToInt()
            val yr = rail.position.y.roundToInt()
            val count = if (odd) 16 else 10
            odd = !odd
            val railChests = chests.subList(0, count)
            val set = HashSet<Cell>()
            val setY = HashSet<Double>()
            railChests.forEach {
                setY.add(it.position.y)
            }
            setY.forEach {
                val c = railChests.filter { x -> x.position.y == it }
                assert(c.size == 2)
                for (x in (c[0].position.x - 0.5).roundToInt() + 1 until (c[1].position.x - 0.5).roundToInt()) {
                    set.add(Cell(x - xr, (it - 0.5).roundToInt() - yr))
                }
            }
            chests.removeAll(railChests)

            result[rail.direction] = set
        }


        return result
    }


    companion object {

        const val CURVED_RAILS =
            "0eNqlmt1O4zAQhd/F1ymKf8fuqyC0Km3ERiopSgJahPru21JBuyij8Z65a0H9cnpsxx8mH+Zx/9q9jP0wm/WH6beHYTLr+w8z9U/DZn/+2fz+0pm16efu2TRm2Dyf342bfm+OjemHXffHrO2xET+yPzz109xvV9vf3TSvpvkwbp66G4g7PjSmG+Z+7rtLiM8377+G1+fHbjxd5Zu1fR3fut3qM0VjXg7T6TOH4XzpE2dFLjfm/fSihPaE3/Vjt738/jPnD6qTEi5e4C5+XcLdxeMC1iNYumL9MjYg2ChiI4INYgkJwNpWTEsI1oppc+38am/m1wKnAPHaLH5r2yJcEr+2tf/PTUUeJeuUXC6vrxynlLn7QFjCAksrZXnF2qjkcjWk2hoSV0NcwgKL63QFOW5Wcrl6C8KNYl7XKrlMXgesNjqH/B5DhuuUXMtwa1cbeW6a+SUsspF5kuMiO5mPcr3IVuaKzCUll+shK0WB4xYll+nBI5ubszK3Whrt1/T1+d/pS0tYZLVZeTp4rzQmZth8UHK5vMhya5OcNym5XF6q3TQLdzdLS1hktbUV9SKbW8livQHZ3IoX8war5HJ5tSrJcb2Sy/WgdUkuL+SS8rII1S5J3F3SLWERl6SKuIhLUpC5RemozLBFyCXl3SJapaNyeZ2Sy+VFdjd/7eH8cpELyeR13M4vF7mQTAY5L7K7eS/nRWTStzI3K7lcD5BMJpGbWt3hG9dDQv52c07O63Tyy+aFbDLJXMgmSe4Bskn5/pCS7kSO7QFZb22U82Yll8sLnUw6kUvQyaQVeyBIJ+X7A0E6Kd8fCNHJLM9fCkoulxfSSXn+UlJyubyQT2Y5L+STFf0Wnady3Iz4JHmxh4ysN5L9ITsll8uLrLck7285KLlcXsgnb7jEcJPSUzmu1icTw9X6JJe3KL2PyVu0PsnkLZBPyj0U6HQyyz1APunlvEHJ5fJGpfdxXK1Pcj1ofZLjan2S66EovY+4/3RD55OlAozscDlXgJ3uxI+r2LZe6VIsGJLKUFEFZJWuApyUYLYKyCtrBg8Sy1hRRVEaFZfYQmpZsaShp0xSrkgMHVb+3JwfmsvjauubB+Ia89aN0+V0O9tAxRFln71Px+NfWo2uSw=="

        val FIELD_FULL = readFieldFromFile("/field_full.txt")

        val FIELD_PREP = readFieldFromFile("/field_prep.txt")

        @JvmStatic
        fun readFieldFromFile(name: String): Field {
            val fileFromResource = this::class.java.getResource(name)!!.readText()
            val decode = Formatter.decode(fileFromResource)
            val dto = Gson().fromJson(decode, BlueprintDTO::class.java)
            return BluePrintFieldExtractor().transformBlueprintToField(dto)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            val toField = FIELD_FULL
            val fixed = toField.state

//            var test = State(emptySet(), emptyMap(), fixed.size)
//            for (x in 3 until fixed.size.x - 3) {*
//                for (y in 3 until fixed.size.y - 3) {
//                    val cell = Cell(x, y)
//                    if (fixed.map[cell] == null) {
//                        val type = if (Utils.isBetween(x, y, toField.chestField) &&
//                            !Utils.isBetween(x, y, toField.roboportsField) ||
//                            !Utils.isBetween(x, y, toField.electricField)
//                        )
//                            BuildingType.REQUEST_CHEST else
//                            BuildingType.PROVIDER_CHEST
//                        test = test.addBuilding(Utils.getBuilding(cell, type))!!
//                    }
//                }
//            }
            Utils.printBest(fixed)
        }
    }
}