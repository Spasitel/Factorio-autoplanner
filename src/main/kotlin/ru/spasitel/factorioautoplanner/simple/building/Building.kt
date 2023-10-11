package ru.spasitel.factorioautoplanner.simple.building


abstract class Building(open val x: Int, open val y: Int) {
    abstract fun toJson(number: Int): String
    abstract val type: Type
    abstract val size: Int
    abstract val symbol: Char

}