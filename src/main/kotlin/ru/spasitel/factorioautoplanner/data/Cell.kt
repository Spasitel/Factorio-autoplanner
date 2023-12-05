package ru.spasitel.factorioautoplanner.data

data class Cell(val x: Int, val y: Int) {

    fun up(): Cell {
        return Cell(x, y - 1)
    }

    fun down(): Cell {
        return Cell(x, y + 1)
    }

    fun left(): Cell {
        return Cell(x - 1, y)
    }

    fun right(): Cell {
        return Cell(x + 1, y)
    }

    fun move(direction: Int): Cell {
        return when (direction) {
            0 -> up()
            2 -> right()
            4 -> down()
            6 -> left()
            else -> throw RuntimeException()
        }
    }

    fun move(direction: Direction): Cell {
        return when (direction) {
            Direction.UP -> up()
            Direction.RIGHT -> right()
            Direction.DOWN -> down()
            Direction.LEFT -> left()
        }
    }

    fun move(direction: Direction, i: Int): Cell {
        return direction.move(this, i)
    }

}
