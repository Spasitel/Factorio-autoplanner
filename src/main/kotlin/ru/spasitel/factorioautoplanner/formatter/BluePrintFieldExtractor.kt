package ru.spasitel.factorioautoplanner.formatter

import com.google.gson.Gson
import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.Field
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.auto.Blueprint
import ru.spasitel.factorioautoplanner.data.auto.BlueprintDTO
import ru.spasitel.factorioautoplanner.data.auto.Entity
import ru.spasitel.factorioautoplanner.data.auto.Position
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import ru.spasitel.factorioautoplanner.data.building.Chest
import ru.spasitel.factorioautoplanner.data.building.StorageTank
import kotlin.math.abs
import kotlin.math.roundToInt

private const val ROBOPORT_DISTANCE = 25

class BluePrintFieldExtractor {

    fun transformBlueprintToField(blueprintDTO: BlueprintDTO): Field {

        val blueprint = normalizeBlueprint(blueprintDTO)

        val (roboportsField, chestField) = getRoboportsAndChests(blueprint)

        val chests = calculateChests(blueprint)
        val electricField = calculateElectricField(blueprint)

        val state = transformBuildingsToEmpty(blueprint)
        val liquids = calculateLiquids(blueprint, state)
        return Field(liquids, chests, /*liquids,*/ roboportsField, chestField, electricField)
    }

    private fun calculateElectricField(blueprint: BlueprintDTO): Pair<Cell, Cell> {
        val substations = blueprint.blueprint.entities.filter { it.name == "substation" }
        return Pair(
            Cell(
                (substations.minOf { it.position.x } - 9).roundToInt(),
                (substations.minOf { it.position.y } - 9).roundToInt()
            ),
            Cell(
                (substations.maxOf { it.position.x } + 8).roundToInt(),
                (substations.maxOf { it.position.y } + 8).roundToInt()
            )
        )
    }

    private fun calculateLiquids(blueprint: BlueprintDTO, state: State): State {


        var liquids = state
        blueprint.blueprint.entities.filter { it.name == "train-stop" }.forEach {
            val name = it.station!!
            if (name.contains("water")) {
                val start = Cell((it.position.x + 1).roundToInt(), (it.position.y).roundToInt())
                val storage = state.map[start]!!
                liquids = liquids.removeBuilding(storage)
                liquids = liquids.addBuilding(
                    Utils.getBuilding(
                        start,
                        BuildingType.STORAGE_TANK,
                        liquid = "water",
                        direction = (storage as StorageTank).direction
                    )
                )!!

//                val cell1 = Cell((it.position.x + 1).roundToInt(), (it.position.y).roundToInt())
//                val cell2 = Cell((it.position.x + 3).roundToInt(), (it.position.y + 2).roundToInt())
//                liquids.getOrPut("water") { HashSet() }.addAll(listOf(cell1, cell2))
            } else if (name.contains("oil")) {
                val start = Cell((it.position.x - 24).roundToInt(), (it.position.y + 1).roundToInt())
                val storage = state.map[start]!!
                liquids = liquids.removeBuilding(storage)
                liquids = liquids.addBuilding(
                    Utils.getBuilding(
                        start,
                        BuildingType.STORAGE_TANK,
                        liquid = "crude-oil",
                        direction = (storage as StorageTank).direction
                    )
                )!!


//                val cell1 = Cell((it.position.x - 22).roundToInt(), (it.position.y + 3).roundToInt())
//                val cell2 = Cell((it.position.x - 24).roundToInt(), (it.position.y + 1).roundToInt())
//                liquids.getOrPut("crude-oil") { HashSet() }.addAll(listOf(cell1, cell2))
            }
        }
        return liquids
    }

    private fun calculateChests(blueprint: BlueprintDTO): Map<String, Set<Chest>> {
        val chests = HashMap<String, MutableSet<Chest>>()
        val stops = HashMap<Cell, String>()
        blueprint.blueprint.entities.filter { it.name == "train-stop" }.forEach {
            val name = it.station!!
            if (name.contains("water") || name.contains("oil")) {
                return@forEach
            }
            stops[Cell(it.position.x.roundToInt(), it.position.y.roundToInt())] = name
        }
        blueprint.blueprint.entities.filter { it.name == "logistic-chest-passive-provider" }
            .forEach {
                val cell = Cell((it.position.x - 0.5).roundToInt(), (it.position.y - 0.5).roundToInt())
                val chest = Utils.getBuilding(cell, BuildingType.PROVIDER_CHEST) as Chest
                val name = stops.keys.minByOrNull { s -> abs(s.x - cell.x) + abs(s.y - cell.y) }.let { s ->
                    stops[s]
                }
                if (name != null) {
                    chests.getOrPut(name) { HashSet() }.add(chest)
                } else {
                    println("Can't find stop for chest $cell")
                }
            }
        return chests
    }

    fun normalizeBlueprint(blueprintDTO: BlueprintDTO): BlueprintDTO {
        val startX = blueprintDTO.blueprint.entities.minOf { it.position.x }.roundToInt() - 2
        val startY = blueprintDTO.blueprint.entities.minOf { it.position.y }.roundToInt() - 1

        val entities = blueprintDTO.blueprint.entities.map {
            Entity(
                it.connections,
                it.control_behavior,
                it.direction,
                it.entity_number,
                it.name,
                it.neighbours,
                Position(it.position.x - startX, it.position.y - startY),
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
            blueprintDTO.blueprint.entities.maxOf { it.position.x }.roundToInt() + 2,
            blueprintDTO.blueprint.entities.maxOf { it.position.y }.roundToInt() + 2
        )
        var state = State(emptySet(), emptyMap(), size)
        val curved = curvedRails()
        for (entity in blueprintDTO.blueprint.entities) {
            val x = entity.position.x
            val y = entity.position.y
            when (entity.name) {
                "pipe", "fast-inserter",
                "rail-signal",
                "long-handed-inserter",
                "logistic-chest-requester",
                "inserter",
                "rail-chain-signal",
                "steel-chest",
                "logistic-chest-passive-provider",
                "pipe-to-ground",
                "express-underground-belt",
                "stack-inserter",
                "medium-electric-pole" -> {
                    val start = Cell((x - 0.5).roundToInt(), (y - 0.5).roundToInt())
                    val addBuilding = state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY))
                    if (addBuilding == null) {
                        println("Can't add building $entity")
                    } else {
                        state = addBuilding
                    }

                }

                "big-electric-pole",
                "train-stop",
                "substation" -> {
                    val start = Cell((x - 1).roundToInt(), (y - 1).roundToInt())
                    state = state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY2))!!

                }

                "radar" -> {
                    val start = Cell((x - 1.5).roundToInt(), (y - 1.5).roundToInt())
                    val addBuilding = state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY3))
                    if (addBuilding == null) {
                        println("Can't add building $entity")
                    } else {
                        state = addBuilding
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
                        println("Can't add building $entity")
                    } else {
                        state = addBuilding
                    }
                }

                "roboport" -> {
                    val start = Cell((x - 2).roundToInt(), (y - 2).roundToInt())
                    state = state.addBuilding(Utils.getBuilding(start, BuildingType.EMPTY4))!!
                }

                "pump",
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
                            println("Can't add building $entity")
                        } else {
                            state = addBuilding
                        }
                        state.addBuilding(Utils.getBuilding(start.left(), BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.right(), BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.down(), BuildingType.EMPTY))?.let { state = it }
                        state.addBuilding(Utils.getBuilding(start.up(), BuildingType.EMPTY))?.let { state = it }
                    }
                }

                "curved-rail" -> {
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

                else -> throw RuntimeException("Unknown entity ${entity.name}")

            }
        }
        return state
    }

    private fun curvedRails(): Map<Int, Set<Cell>> {
        val blueprint = Gson().fromJson(Formatter.decode(curvedRails), BlueprintDTO::class.java)
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

        const val straightRails =
            "0eNqlluFugyAUhd/l/sZGQAF9lWZZrCWWxGIjdFnT+O7Dmm3NAqGDfyLh89zjPcAdDuNVXmalLbR3UP2kDbT7Oxg16G5c39nbRUILysozINDdeR3NnRphQaD0UX5CixcUXTJOgzJW9UV/ksYWxk5zN8gnCFneEEhtlVVyE/EY3N719XyQs/tKjIXgMhm3fNKrCocsGK92NYKbe2wo2dXLKvQPlvxgjXV1DSdbPMrz0eg3y1UMRzXLfpvnHi5NkUuicqsErHhyofRj60ws9mPZ6+aKkLnEw+UpcsuoCyITG3ChScHiKBaXmdyACxj/IxLN71/zoUhKCni8dJrJDZWeEi9ex/XWmZtXSC/L3GVC3JSA8RdaNiVhTMT1pkSMxfuMlK9HgfHABkZ94JTTjMUbgqQEjsUbmNA8g0kT4FZ5ejeuuzk8bhvt030GwYeczXaCCFzxhnAuqKCULcsXF+7onw=="

        const val diagonalRails =
            "0eNqllNGKhCAUht/lXNswamX1KsOyNI00QlmoDRvhu68VuxNL4WJ3af2fn4fTmeDeDLxXQhooJhBVJzUUtwm0qGXZzHtm7DkUIAxvAYEs23nVdLXQRlRR9eTaRNp0qqw5WARCPvgXFNgiL0SVotlEyG7kJZQZ3M5vav0iwpsktR8IuDTCCL7qL4vxUw7tnStn4xNH0HfaxTs5H+2QUcriS4JgdI85JZfEznZ/sOQtZdxl6qeJljvt0egPy1UGHkLxan3Pdrg0RJd4deMQbPLG4n1scrK4131serIIB1gWgsXeImQB2DTz2uYhWOa1xdf/d27KDjqX7oFD/rTU3wyYhHD9vYvpuQKT/IAbn/NduW6qLROz2ExpBC+u9BIjGY5ZThjLaEZpau03jMzjSQ=="

        const val curvedRails =
            "0eNqlmt1O4zAQhd/F1ymKf8fuqyC0Km3ERiopSgJahPru21JBuyij8Z65a0H9cnpsxx8mH+Zx/9q9jP0wm/WH6beHYTLr+w8z9U/DZn/+2fz+0pm16efu2TRm2Dyf342bfm+OjemHXffHrO2xET+yPzz109xvV9vf3TSvpvkwbp66G4g7PjSmG+Z+7rtLiM8377+G1+fHbjxd5Zu1fR3fut3qM0VjXg7T6TOH4XzpE2dFLjfm/fSihPaE3/Vjt738/jPnD6qTEi5e4C5+XcLdxeMC1iNYumL9MjYg2ChiI4INYgkJwNpWTEsI1oppc+38am/m1wKnAPHaLH5r2yJcEr+2tf/PTUUeJeuUXC6vrxynlLn7QFjCAksrZXnF2qjkcjWk2hoSV0NcwgKL63QFOW5Wcrl6C8KNYl7XKrlMXgesNjqH/B5DhuuUXMtwa1cbeW6a+SUsspF5kuMiO5mPcr3IVuaKzCUll+shK0WB4xYll+nBI5ubszK3Whrt1/T1+d/pS0tYZLVZeTp4rzQmZth8UHK5vMhya5OcNym5XF6q3TQLdzdLS1hktbUV9SKbW8livQHZ3IoX8war5HJ5tSrJcb2Sy/WgdUkuL+SS8rII1S5J3F3SLWERl6SKuIhLUpC5RemozLBFyCXl3SJapaNyeZ2Sy+VFdjd/7eH8cpELyeR13M4vF7mQTAY5L7K7eS/nRWTStzI3K7lcD5BMJpGbWt3hG9dDQv52c07O63Tyy+aFbDLJXMgmSe4Bskn5/pCS7kSO7QFZb22U82Yll8sLnUw6kUvQyaQVeyBIJ+X7A0E6Kd8fCNHJLM9fCkoulxfSSXn+UlJyubyQT2Y5L+STFf0Wnady3Iz4JHmxh4ysN5L9ITsll8uLrLck7285KLlcXsgnb7jEcJPSUzmu1icTw9X6JJe3KL2PyVu0PsnkLZBPyj0U6HQyyz1APunlvEHJ5fJGpfdxXK1Pcj1ofZLjan2S66EovY+4/3RD55OlAozscDlXgJ3uxI+r2LZe6VIsGJLKUFEFZJWuApyUYLYKyCtrBg8Sy1hRRVEaFZfYQmpZsaShp0xSrkgMHVb+3JwfmsvjauubB+Ia89aN0+V0O9tAxRFln71Px+NfWo2uSw=="
        const val FIELD =
            "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"

        @JvmStatic
        fun main(args: Array<String>) {
            val decodeField = Formatter.decode(FIELD)
            val dto = Gson().fromJson(decodeField, BlueprintDTO::class.java)
            val toField = BluePrintFieldExtractor().transformBlueprintToField(dto)
            val fixed = toField.state

            var test = State(emptySet(), emptyMap(), fixed.size)
            for (x in 3 until fixed.size.x - 3) {
                for (y in 3 until fixed.size.y - 3) {
                    val cell = Cell(x, y)
                    if (fixed.map[cell] == null) {
                        val type = if (Utils.isBetween(x, y, toField.chestField) &&
                            !Utils.isBetween(x, y, toField.roboportsField) ||
                            !Utils.isBetween(x, y, toField.electricField)
                        )
                            BuildingType.REQUEST_CHEST else
                            BuildingType.PROVIDER_CHEST
                        test = test.addBuilding(Utils.getBuilding(cell, type))!!
                    }
                }
            }
            Utils.printBest(test)
        }
    }
}