package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place

abstract class Building(open val place: Place) {
    var id: Int = -1
    abstract fun toJson(number: Int): String
    abstract val type: BuildingType
    abstract val symbol: Char

    fun toJson(): String {
        if (id == -1) {
            id = idCounter++
        }
        return toJson(id)
    }

    fun enumerate() {
        if (id == -1) {
            id = idCounter++
        }
    }

    override fun toString(): String {
        return type.toString() + "{" +
                place.start +
                '}'
    }

    companion object {
        var idCounter = 1
    }

}
