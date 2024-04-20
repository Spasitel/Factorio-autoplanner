package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.building.BuildingType

data class SimpleState(val buildings: Map<Cell, BuildingType>) {
    companion object {
        fun fromState(state: State): SimpleState {
            val map = state.buildings.associate { it.place.start to it.type }
            return SimpleState(map)
        }
    }
}
