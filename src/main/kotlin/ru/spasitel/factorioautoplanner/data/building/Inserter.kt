package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.Direction
import ru.spasitel.factorioautoplanner.data.Place
import java.util.*


data class Inserter(
    override val place: Place,
    val direction: Int,
    var kind: String = "stack-inserter",
    var condition: Triple<Int, String, Int>? = null // connection, item, amount
) :
    Building(place) {
    override fun toJson(number: Int): String {
        var connections = ""
        if (condition != null) {
            val (connection, item, amount) = condition!!
            //,"control_behavior":{"circuit_condition":{"first_signal":{"type":"item","name":"copper-plate"},"constant":100,"comparator":"<"}},"connections":{"1":{"red":[{"entity_id":1}]}}
            connections =
                ",\"control_behavior\":{\"circuit_condition\":{\"first_signal\":{\"type\":\"item\",\"name\":\"$item\"},\"constant\":$amount,\"comparator\":\"<\"}},\"connections\":{\"1\":{\"red\":[{\"entity_id\":$connection}]}}"
        }

        return String.format(
            Locale.US,
            INSERTER,
            number,
            kind,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0,
            direction,
            connections
        )
    }

    override val type: BuildingType
        get() = BuildingType.INSERTER

    override val symbol: Char
        get() = when (direction) {
            4 -> '^'
            6 -> '>'
            0 -> 'v'
            2 -> '<'
            else -> throw RuntimeException("Unknown direction $direction")
        }

    fun alongX(): Boolean {
        return direction == 2 || direction == 6
    }

    fun to(): Cell {
        val back = Direction.fromInt(direction).turnBack()
        if (kind == "long-handed-inserter") {
            return place.start.move(back).move(back)
        }
        return place.start.move(back)
    }

    fun from(): Cell {
        if (kind == "long-handed-inserter") {
            return place.start.move(direction).move(direction)
        }
        return place.start.move(direction)
    }

    override fun toString(): String {
        return super.toString()
    }

    companion object {
        const val INSERTER =
            "{\"entity_number\":%d,\"name\":\"%s\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"direction\":%d%s},"
    }
}