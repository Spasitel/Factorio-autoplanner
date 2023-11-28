package ru.spasitel.factorioautoplanner.planner

import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.BuildingType


object GreedyMain {
    private val SIZE = 18

    @JvmStatic
    fun main(args: Array<String>) {
        var start: State? = State(emptySet(), emptyMap(), Cell(SIZE, SIZE))
        start = start?.addBuilding(Utils.getBuilding(Cell(0, 0), BuildingType.EMPTY))
        start = start?.addBuilding(Utils.getBuilding(Cell(SIZE - 1, 0), BuildingType.EMPTY))
        start = start?.addBuilding(Utils.getBuilding(Cell(0, SIZE - 1), BuildingType.EMPTY))
        start = start?.addBuilding(Utils.getBuilding(Cell(SIZE - 1, SIZE - 1), BuildingType.EMPTY))
        val greedy = GreedyPlanner().greedy(start!!, listOf(BuildingType.SMELTER, BuildingType.BEACON))
        Utils.printBest(greedy)
        UpgradePlanner().upgrade(greedy, Int.MAX_VALUE)
    }

}