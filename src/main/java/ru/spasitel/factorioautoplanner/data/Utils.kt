package ru.spasitel.factorioautoplanner.data

object Utils {
    fun sellsForBuilding(start: Sell, size: Int): Set<Sell> {
        val sells = HashSet<Sell>()
        sells.add(start)
        for (x in 0 until size - 1) {
            for (y in 0 until size - 1) {
                sells.add(Sell.get(start.x + x, start.y + y))
            }
        }
        return sells
    }
}