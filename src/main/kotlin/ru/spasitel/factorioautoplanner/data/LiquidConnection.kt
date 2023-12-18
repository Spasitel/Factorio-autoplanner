package ru.spasitel.factorioautoplanner.data

data class LiquidConnection(val from: Cell, val to: Cell, val liquid: String, val direction: Direction) {
    fun opposite(): LiquidConnection {
        return LiquidConnection(to, from, liquid, direction.turnBack())
    }
}
