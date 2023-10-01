package ru.spasitel.factorioautoplanner.data

import java.util.*

enum class Recipe(var output: Item, var input: Map<Item, Int>, var secondToProduce: Double, var outCount: Int) {
    IRON_SMELTING(Item.IRON, Collections.singletonMap(Item.IRON_ORE, 1), 3.2, 1);
}