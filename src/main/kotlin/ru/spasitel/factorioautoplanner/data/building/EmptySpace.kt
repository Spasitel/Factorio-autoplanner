package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place

data class EmptySpace(override val place: Place, val size: Int) : Building(place) {
    override fun toJson(number: Int): String {
        throw RuntimeException()
    }

    override val type: BuildingType
        get() = when (size) {
            1 -> BuildingType.EMPTY
            2 -> BuildingType.EMPTY2
            3 -> BuildingType.EMPTY3
            4 -> BuildingType.EMPTY4
            else -> throw RuntimeException()
        }
    override val symbol: Char
        get() = 'x'

    override fun toString(): String {
        return super.toString()
    }
}