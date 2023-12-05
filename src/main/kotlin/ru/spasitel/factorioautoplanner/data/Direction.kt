package ru.spasitel.factorioautoplanner.data

enum class Direction(
    val x: Int,
    val y: Int,
    val symbol: Char,
    val direction: Int
) {
    UP(0, -1, '^', 0),
    RIGHT(1, 0, '>', 2),
    DOWN(0, 1, 'v', 4),
    LEFT(-1, 0, '<', 6);

    fun turnRight(): Direction {
        return when (this) {
            UP -> RIGHT
            RIGHT -> DOWN
            DOWN -> LEFT
            LEFT -> UP
        }
    }

    fun turnLeft(): Direction {
        return when (this) {
            UP -> LEFT
            RIGHT -> UP
            DOWN -> RIGHT
            LEFT -> DOWN
        }
    }

    fun turnBack(): Direction {
        return when (this) {
            UP -> DOWN
            RIGHT -> LEFT
            DOWN -> UP
            LEFT -> RIGHT
        }
    }

    fun move(cell: Cell): Cell {
        return Cell(cell.x + x, cell.y + y)
    }

    fun move(cell: Cell, times: Int): Cell {
        return Cell(cell.x + x * times, cell.y + y * times)
    }

    companion object {
        fun fromInt(direction: Int): Direction {
            return when (direction) {
                0 -> UP
                2 -> RIGHT
                4 -> DOWN
                6 -> LEFT
                else -> throw RuntimeException("Unknown direction $direction")
            }
        }

        fun fromCells(from: Cell, to: Cell): Direction {
            return when (to) {
                from.move(UP) -> UP
                from.move(RIGHT) -> RIGHT
                from.move(DOWN) -> DOWN
                from.move(LEFT) -> LEFT
                else -> throw RuntimeException("Unknown direction from $from to $to")
            }
        }
    }
}
