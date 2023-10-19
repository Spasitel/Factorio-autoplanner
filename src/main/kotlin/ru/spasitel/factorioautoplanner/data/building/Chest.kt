package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*


data class Chest(override val place: Place, private val provider: Boolean) : Building(place) {
    override fun toJson(number: Int): String {
        return if (provider) String.format(
            Locale.US,
            PROVIDER_CHEST,
            number,
            place.start.x + 0.5,
            place.start.y + 0.5
        ) else String.format(Locale.US, REQUEST_CHEST, number, place.start.x + 0.5, place.start.y + 0.5)
    }

    override val type: BuildingType
        get() = if (provider) BuildingType.PROVIDER_CHEST else BuildingType.REQUEST_CHEST

    override val symbol: Char
        get() = if (provider) 'p' else 'r'

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        const val PROVIDER_CHEST =
            "{\"entity_number\":%d,\"name\":\"logistic-chest-passive-provider\",\"position\":{\"x\":%.1f,\"y\":%.1f}},"
        const val REQUEST_CHEST =
            "{\"entity_number\":%d,\"name\":\"logistic-chest-requester\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"request_filters\":[{\"index\":1,\"name\":\"iron-plate\",\"count\":300}]},"
    }
}