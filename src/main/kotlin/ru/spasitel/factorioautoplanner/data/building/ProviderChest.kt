package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class ProviderChest(
    override val place: Place,
    val items: MutableSet<String> = HashSet(),
    val connections: MutableSet<Int> = HashSet()
) : Building(place) {
    override fun toJson(number: Int): String {
        //,"connections":{"1":{"red":[{"entity_id":2},{"entity_id":4}]}}
        var str = ""
        if (this.connections.isNotEmpty()) {
            str += ",\"connections\":{\"1\":{\"red\":["
            str += connections.joinToString(",") {
                "{\"entity_id\":$it}"
            }
            str += "]}}"
        }

        return String.format(
            Locale.US,
            PROVIDER_CHEST,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0,
            str
        )
    }

    override val type: BuildingType
        get() = BuildingType.PROVIDER_CHEST

    override val symbol: Char
        get() = 'p'

    override fun toString(): String {
        return super.toString() + " $items"
    }

    companion object {
        const val PROVIDER_CHEST =
            "{\"entity_number\":%d,\"name\":\"logistic-chest-passive-provider\",\"position\":{\"x\":%.1f,\"y\":%.1f}%s},"
    }
}
