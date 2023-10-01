package ru.spasitel.factorioautoplanner.simple.building

import lombok.EqualsAndHashCode
import lombok.ToString

@ToString
@EqualsAndHashCode
abstract class Building(val x: Int, val y: Int) {
    abstract fun toJson(number: Int): String
    abstract val type: Type
    abstract val size: Int
    abstract val symbol: Char

}