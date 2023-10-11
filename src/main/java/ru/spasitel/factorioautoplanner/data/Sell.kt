package ru.spasitel.factorioautoplanner.data

data class Sell(val x: Int, val y: Int) {
    companion object {
        private val CACHE = HashMap<Pair<Int, Int>, Sell>()
        fun get(x: Int, y: Int): Sell {
            val key = Pair(x, y)
            if (CACHE.containsKey(key)) {
                return CACHE[key]!!
            }
            val sell = Sell(x, y)
            CACHE[key] = sell
            return sell
        }
    }

    fun up(): Sell {
        return get(x, y - 1)
    }

    fun down(): Sell {
        return get(x, y + 1)
    }

    fun left(): Sell {
        return get(x - 1, y)
    }

    fun right(): Sell {
        return get(x + 1, y)
    }
}
