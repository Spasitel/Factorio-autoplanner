package ru.spasitel.factorioautoplanner.data

data class Place(val cells: Set<Cell>, val start: Cell) {

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Place

        if (start != other.start) return false
        if (cells != other.cells) return false

        return true
    }

    override fun hashCode(): Int {
        var result = start.hashCode()
        result = 31 * result + cells.size * 371
        return result
    }
}
