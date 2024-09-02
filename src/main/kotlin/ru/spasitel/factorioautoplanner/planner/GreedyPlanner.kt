package ru.spasitel.factorioautoplanner.planner

import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.Field
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.Building
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import ru.spasitel.factorioautoplanner.data.building.ProviderChest

class GreedyPlanner {

    fun greedy(state: State, types: List<BuildingType>): State {
        var currentState = state
        while (true) {
            val newState = greedyStep(currentState, types) ?: return currentState
            currentState = newState
        }
    }

    private fun greedyStep(state: State, types: List<BuildingType>): State? {
        val forBuild = getClosestPlace(state) ?: return null
        // try all buildings that intersect with forBuild
        var bestState: State? = null
        var bestScore = 0.0
        for (type in types) {
            val newStates: Set<State> =
                generateNewStates(type, state, forBuild)

            for (newState in newStates) {
                if (newState.score.value > bestScore) {
                    bestScore = newState.score.value
                    bestState = newState
                }
            }
        }
        return bestState
    }

    fun generateNewStates(
        type: BuildingType,
        state: State,
        forBuild: Cell
    ): Set<State> {
        return if (type == BuildingType.BEACON)
            generateBeaconStates(state, forBuild)
        else generateStates(state, forBuild, type)
    }

    //greedy main
    private fun generateStates(state: State, forBuild: Cell, type: BuildingType): Set<State> {
        val newStates = HashSet<State>()
        for (x in -4 until 5) {
            for (y in -4 until 5) {
                val start = Cell(forBuild.x + x, forBuild.y + y)
                // build and add to newStates
                val building = Utils.getBuilding(start, type)
                val withoutChests = state.addBuilding(building) ?: continue
                val withInputChest = addChests(withoutChests, building, BuildingType.REQUEST_CHEST) //greedy main
                for (withInputChestState in withInputChest) {
                    val withOutputChest =
                        addChests(withInputChestState, building, BuildingType.PROVIDER_CHEST) //greedy main
                    for (withOutputChestState in withOutputChest) {
                        if (!withOutputChestState.freeCells.contains(forBuild))
                            newStates.add(withOutputChestState)
                    }
                }
            }
        }
        return newStates
    }

    public fun addChests(
        withoutChests: State,
        building: Building,
        type: BuildingType,
        items: Set<String> = setOf(),
        field: Field? = null
    ): Set<State> {
        val newStates = HashSet<State>()
        for (chest in Utils.chestsPositions(building)) {
            val triple = prepareChestsBuildings(withoutChests, chest, type, field, items) ?: continue
            triple.first.addBuildings(listOf(triple.second, triple.third))?.let {
                newStates.add(it)
            }
        }
        return newStates
    }

    public fun prepareChestsBuildings(
        withoutChests: State,
        chest: Triple<Cell, Cell, Int>,
        type: BuildingType,
        field: Field?,
        items: Set<String>
    ): Triple<State, Building, Building>? {
        if (withoutChests.map.containsKey(chest.first) ||
            (withoutChests.map.containsKey(chest.second)
                    && withoutChests.map.getValue(chest.second).type != type)
        ) {
            return null
        }
        if (field != null && !Utils.isBetween(chest.second, field.chestField)) return null
        if (field != null && withoutChests.map.containsKey(chest.second) &&
            withoutChests.map[chest.second]!! is ProviderChest &&
            ((withoutChests.map[chest.second] as ProviderChest).items.isEmpty() ||
                    (withoutChests.map[chest.second] as ProviderChest).items.first() in TechnologyTreePlanner.base
                    )
        ) {
            return null
        }

        val inserterBuilding = Utils.getBuilding(
            chest.first,
            BuildingType.INSERTER,
            direction = if (type == BuildingType.PROVIDER_CHEST) (chest.third + 4).mod(8) else chest.third
        )
        var newItems = items.toMutableSet()
        if (type == BuildingType.PROVIDER_CHEST &&
            items.isNotEmpty() &&
            withoutChests.map.containsKey(chest.second)
        ) {
            newItems = (withoutChests.map.getValue(chest.second) as ProviderChest).items.plus(items).toMutableSet()
        }
        val chestBuilding = Utils.getBuilding(
            chest.second,
            type,
            items = newItems
        )

        val state = if (!withoutChests.map.containsKey(chest.second) || type != BuildingType.PROVIDER_CHEST) {
            withoutChests
        } else {
            withoutChests.removeBuilding(withoutChests.map[chest.second]!!)
        }
        return Triple(state, inserterBuilding, chestBuilding)
    }

    private fun generateBeaconStates(state: State, forBuild: Cell): Set<State> {
        val newStates = HashSet<State>()
        for (x in -2 until 3) {
            for (y in -2 until 3) {
                val start = Cell(forBuild.x + x, forBuild.y + y)
                // build and add to newStates
                val building = Utils.getBuilding(start, BuildingType.BEACON)
                state.addBuilding(building)?.let { newStates.add(it) }
            }
        }
        return newStates
    }

    // choose the closest place for building
    private fun getClosestPlace(state: State): Cell? {
        var minDistance = Int.MAX_VALUE
        var closestStart: Cell? = null
        for (start in state.freeCells) {
            val distance = getDistance(state, start)
            if (distance < minDistance) {
                minDistance = distance
                closestStart = start
            }
        }

        return closestStart
    }

    private fun getDistance(state: State, start: Cell): Int {
        return start.x * start.x + start.y * start.y
    }
}
