package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place

data class EmptySpace(override val place: Place) : Building(place) {
    override fun toJson(number: Int): String {
        throw RuntimeException()
    }

    override val type: BuildingType
        get() = BuildingType.EMPTY
    override val symbol: Char
        get() = 'x'
}