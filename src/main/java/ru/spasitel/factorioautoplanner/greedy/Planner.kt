package ru.spasitel.factorioautoplanner.greedy

import ru.spasitel.factorioautoplanner.data.*

class Planner {


    fun greedy(state: State, types: List<BuildingType>): State {
        val forBuild = getClosestPlace(state) ?: return state
        // try all buildings that intersect with forBuild
        var bestState = state
        var bestScore = state.score.value
        for (type in types) {
            val newStates: Set<State> =
                if (type == BuildingType.BEACON)
                    gereateBeaconStates(state, forBuild)
                else generateStates(state, forBuild, type)


            for (newState in newStates) {
                if (newState.score.value > bestScore) {
                    bestScore = newState.score.value
                    bestState = newState
                }
            }
        }
        return bestState
    }

    private fun generateStates(state: State, forBuild: Place, type: BuildingType): Set<State> {
        TODO("Not yet implemented")
    }

    private fun gereateBeaconStates(state: State, forBuild: Place): Set<State> {
        TODO("Not yet implemented")
    }


    //choose the closest place for building
    private fun getClosestPlace(state: State): Place? {
        var minDistance = Int.MAX_VALUE
        var closestStart: Sell? = null
        for (start in state.freeSells.value) {
            val distance = getDistance(state, start)
            if (distance < minDistance) {
                minDistance = distance
                closestStart = start
            }
        }
        if (closestStart == null) return null

        return Place(Utils.sellsForBuilding(closestStart, BuildingType.BEACON.size), closestStart)
    }

    private fun getDistance(state: State, start: Sell): Int {
        return start.x * start.x + start.y * start.y
    }
}