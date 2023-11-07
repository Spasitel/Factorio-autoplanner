package ru.spasitel.factorioautoplanner

import ru.spasitel.factorioautoplanner.greedy.GlobalPlanner

object Main {

    @JvmStatic
    fun main(args: Array<String>) {
        GlobalPlanner().readRecipesFromFile()
    }
}