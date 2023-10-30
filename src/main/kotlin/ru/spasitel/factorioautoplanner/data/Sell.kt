package ru.spasitel.factorioautoplanner.data

data class Sell(val x: Int, val y: Int) {

    fun up(): Sell {
        return Sell(x, y - 1)
    }

    fun down(): Sell {
        return Sell(x, y + 1)
    }

    fun left(): Sell {
        return Sell(x - 1, y)
    }

    fun right(): Sell {
        return Sell(x + 1, y)
    }
}
