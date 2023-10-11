package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place

abstract class Building(open val place: Place) {
    abstract fun toJson(number: Int): String
    abstract val type: BuildingType
    abstract val symbol: Char
}
