package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.LiquidConnection

interface LiquidBuilding {
    var liquid: String
    fun getLiquidConnections(): List<LiquidConnection>
}