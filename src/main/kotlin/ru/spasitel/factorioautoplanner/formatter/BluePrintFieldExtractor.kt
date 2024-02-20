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