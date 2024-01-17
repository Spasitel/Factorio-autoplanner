package ru.spasitel.factorioautoplanner.data.building

import ru.spasitel.factorioautoplanner.data.Place
import java.util.*

data class TrainStop(override val place: Place, val name: String, val direction: Int) : Building(place) {
    override fun toJson(number: Int): String {
        return String.format(
            Locale.US,
            JSON,
            number,
            place.start.x + type.size / 2.0,
            place.start.y + type.size / 2.0,
            name,
            direction
        )
    }

    override val type: BuildingType
        get() = BuildingType.TRAIN_STOP
    override val symbol: Char
        get() = 'T'

    companion object {
        private const val JSON =
            "{\"entity_number\":%d,\"name\":\"train-stop\",\"position\":{\"x\":%.1f,\"y\":%.1f},\"station\":\"%s\",\"direction\":%d},"
    }

    override fun toString(): String {
        return super.toString()
    }
}

