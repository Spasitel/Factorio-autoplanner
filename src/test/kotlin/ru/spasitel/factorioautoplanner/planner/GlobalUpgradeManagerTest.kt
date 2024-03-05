package ru.spasitel.factorioautoplanner.planner

import org.junit.jupiter.api.Test
import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.BuildingType

class GlobalUpgradeManagerTest {

    @Test
    fun getPairsToRemoveTest() {
        val um = GlobalUpgradeManager(GlobalPlanner())

        val toRemove = mutableListOf(
            Utils.getBuilding(Cell(0, 0), BuildingType.EMPTY)
        )
        for (i in 1 until 10) {
            toRemove.add(Utils.getBuilding(Cell(i, i), BuildingType.EMPTY))
        }
        for (attempts in 0 until 20) {

            val pairs = um.getPairsToRemove(toRemove, attempts)
            println("$attempts: $pairs")
        }
    }
}