package ru.spasitel.factorioautoplanner.simple.building

import java.util.*


data class Smelter(override val x: Int, override val y: Int) : Building(x, y) {
    val connected: MutableMap<Chest, Inserter> = HashMap()
    override fun toJson(number: Int): String {
        return String.format(Locale.US, SMELTER, number, x + 1.5, y + 1.5)
    }

    override val type: Type
        get() = Type.SMELTER
    override val size: Int
        get() = 3
    override val symbol: Char
        get() = 's'

    companion object {
        private const val SMELTER =
            "{\"entity_number\":%d,\"name\":\"electric-furnace\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"speed-module\":2}},"
    }
}