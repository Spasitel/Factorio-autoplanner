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