package ru.spasitel.factorioautoplanner.simple.building

import lombok.EqualsAndHashCode
import lombok.ToString
import java.util.*

@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
class Beacon(x: Int, y: Int) : Building(x, y) {
    override val type: Type
        get() = Type.BEACON
    override val size: Int
        get() = 3
    override val symbol: Char
        get() = 'b'

    override fun toJson(number: Int): String {
        return String.format(Locale.US, BEACON, number, x + 1.5, y + 1.5)
    }

    companion object {
        private const val BEACON =
            "{\"entity_number\":%d,\"name\":\"beacon\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"items\":{\"speed-module-3\":2}},"
    }
}