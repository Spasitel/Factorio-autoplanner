package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.LiquidConnection

interface LiquidConnectionsBuilding {
    fun getLiquidConnections(): List<LiquidConnection>
}