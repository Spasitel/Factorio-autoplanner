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