package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*


data class Chest(override val place: Place, private val provider: Boolean) : Building(place) {
    override fun toJson(number: Int): String {
        return if (provider) String.format(
            Locale.US,
            PROVIDER_CHEST,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0
        ) else String.format(
            Locale.US,
            REQUEST_CHEST,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0
        )
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
        private const val IRON_PLATE = "iron-plate"
        private const val IRON_ORE = "iron-ore"
        private const val COPPER_ORE = "copper-ore"
        const val REQUEST_CHEST =
            "{\"entity_number\":%d,\"name\":\"logistic-chest-requester\",\"position\":{\"x\":%.1f,\"y\":%.1f}},"
//            "{\"entity_number\":%d,\"name\":\"logistic-chest-requester\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"request_filters\":[{\"index\":1,\"name\":\"$IRON_ORE\",\"count\":300}]},"
    }
}