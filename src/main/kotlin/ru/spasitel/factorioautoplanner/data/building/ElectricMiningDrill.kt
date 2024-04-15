package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place

class ElectricMiningDrill(override val place: Place) : Building(place) {
    override fun toJson(number: Int): String {
        return ""
    }

    override val type: BuildingType
        get() = BuildingType.ELECTRIC_MINING_DRILL
    override val symbol: Char
        get() = 'E'

    override fun toString(): String {
        return super.toString()
    }
}
