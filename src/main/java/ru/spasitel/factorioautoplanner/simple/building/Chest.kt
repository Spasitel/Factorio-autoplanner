package ru.spasitel.factorioautoplanner.simple.building

import java.util.*


data class Chest(override val x: Int, override val y: Int) : Building(x, y) {
    private val provider: Boolean? = null
    override fun toJson(number: Int): String {
        return if (java.lang.Boolean.FALSE == provider) String.format(
            Locale.US,
            PROVIDER_CHEST,
            number,
            x + 0.5,
            y + 0.5
        ) else String.format(Locale.US, REQUEST_CHEST, number, x + 0.5, y + 0.5)
    }

    override val type: Type
        get() = if (java.lang.Boolean.FALSE == provider) Type.PROVIDER_CHEST else Type.REQUEST_CHEST
    override val size: Int
        get() = 1
    override val symbol: Char
        get() = if (java.lang.Boolean.FALSE == provider) 'p' else 'r'

    companion object {
        const val PROVIDER_CHEST =
            "{\"entity_number\":%d,\"name\":\"logistic-chest-passive-provider\",\"position\":{\"x\":%.1f,\"y\":%.1f}},"
        const val REQUEST_CHEST =
            "{\"entity_number\":%d,\"name\":\"logistic-chest-requester\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"request_filters\":[{\"index\":1,\"name\":\"iron-plate\",\"count\":300}]},"
    }
}