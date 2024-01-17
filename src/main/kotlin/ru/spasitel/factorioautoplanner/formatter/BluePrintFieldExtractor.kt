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
                    while (state.map[cell] == null ||
                        state.map[cell]!!.type != BuildingType.UNDERGROUND_PIPE ||
                        (state.map[cell]!! as UndergroundPipe).direction != back.direction
                    ) {
                        cell = cell.move(back)
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
//            var newState = State(emptySet(), emptyMap(), state.size)
//            state.buildings.forEach {
//                if (it is LiquidBuilding && it.liquid == liquid) {
//                    newState = newState.addBuilding(it)!!
//                }
//            }
//
//            logger.info {"=======$liquid========"}
//
//            Utils.printBest(newState)

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
                "long-handed-inserter",
                "rail-signal",
                "rail-chain-signal",
                "express-underground-belt",
                "logistic-chest-active-provider",
                "logistic-chest-storage",
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

        const val FIELD =
            "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"
        @JvmStatic
        fun main(args: Array<String>) {
            val decodeField = Formatter.decode(FIELD)
            val dto = Gson().fromJson(decodeField, BlueprintDTO::class.java)
            val toField = BluePrintFieldExtractor().transformBlueprintToField(dto)
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