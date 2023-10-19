package ru.spasitel.factorioautoplanner.greedy

import ru.spasitel.factorioautoplanner.data.Sell
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import ru.spasitel.factorioautoplanner.formatter.Formatter
import ru.spasitel.factorioautoplanner.simple.Main


object Main {
    private val SIZE = 18

    @JvmStatic
    fun main(args: Array<String>) {
        var start: State? = State(emptySet(), emptyMap(), Sell(SIZE, SIZE))
        start = start?.addBuilding(Utils.getBuilding(Sell.get(0, 0), BuildingType.EMPTY))
        start = start?.addBuilding(Utils.getBuilding(Sell.get(SIZE - 1, 0), BuildingType.EMPTY))
        start = start?.addBuilding(Utils.getBuilding(Sell.get(0, SIZE - 1), BuildingType.EMPTY))
        start = start?.addBuilding(Utils.getBuilding(Sell.get(SIZE - 1, SIZE - 1), BuildingType.EMPTY))
        val greedy = GreedyPlanner().greedy(start!!, listOf(BuildingType.SMELTER, BuildingType.BEACON))
        printBest(greedy)
        UpgradePlanner().upgrade(greedy, Int.MAX_VALUE)
    }

    @JvmStatic
    fun printBest(best: State) {
        println(best)
        var bCount = 1
        var json = StringBuilder(Main.START_JSON)
        for (b in best.buildings) {
            if (b.type == BuildingType.EMPTY) continue
            json.append(b.toJson(bCount))
            bCount++
        }
        json = StringBuilder(json.substring(0, json.length - 1))
        json.append(Main.END_JSON)
        println(Formatter.encode(json.toString()))
    }
}