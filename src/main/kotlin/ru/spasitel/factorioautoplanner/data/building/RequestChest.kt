package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class RequestChest(override val place: Place, val items: MutableMap<String, Int> = HashMap()) : Building(place) {
    override fun toJson(number: Int): String {
        var filters = ""
        if (items.isNotEmpty()) {
            filters += ",\"request_filters\":["
            filters += items.entries.joinToString(",") { (item, count) ->
                "{\"index\":${items.keys.indexOf(item)},\"name\":\"$item\",\"count\":$count}"
            }
            filters += "]"
        }
        return String.format(
            Locale.US,
            REQUEST_CHEST,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0,
            filters
        )
    }

    override val type: BuildingType
        get() = BuildingType.REQUEST_CHEST

    override val symbol: Char
        get() = 'r'

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        const val REQUEST_CHEST =
            "{\"entity_number\":%d,\"name\":\"logistic-chest-requester\",\"position\":{\"x\":%.1f,\"y\":%.1f}%s},"
//            "{\"entity_number\":%d,\"name\":\"logistic-chest-requester\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"request_filters\":[{\"index\":1,\"name\":\"$IRON_ORE\",\"count\":300}]},"
    }
}
