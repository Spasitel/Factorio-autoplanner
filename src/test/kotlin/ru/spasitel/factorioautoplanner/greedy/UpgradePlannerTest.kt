package ru.spasitel.factorioautoplanner.greedy

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import ru.spasitel.factorioautoplanner.data.Sell
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.BuildingType

class UpgradePlannerTest {

    @Test
    fun findNextToDelete() {

        var state = State(emptySet(), emptyMap(), Sell(18, 18))
        val building1 = Utils.getBuilding(Sell(1, 0), BuildingType.SMELTER)
        val building2 = Utils.getBuilding(Sell(3, 6), BuildingType.SMELTER)
        val building3 = Utils.getBuilding(Sell(4, 3), BuildingType.SMELTER)
        val building4 = Utils.getBuilding(Sell(7, 2), BuildingType.SMELTER)
        val building5 = Utils.getBuilding(Sell(7, 6), BuildingType.SMELTER)
        state =
            state.addBuilding(building2)!!.addBuilding(building1)!!.addBuilding(building3)!!.addBuilding(building4)!!
                .addBuilding(building5)!!
        var deleted = UpgradePlanner().findNextToDelete(state = state, deleted = emptyList())
        assert(deleted!!.size == 1)
        assert(deleted[0] == building1)
        deleted = UpgradePlanner().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 1)
        assert(deleted[0] == building2)
        deleted = UpgradePlanner().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 1)
        assert(deleted[0] == building3)

        deleted = UpgradePlanner().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 1)
        assert(deleted[0] == building4)

        deleted = UpgradePlanner().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 1)
        assert(deleted[0] == building5)

        deleted = UpgradePlanner().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 2)
        assert(deleted[0] == building1)
        assert(deleted[1] == building2)

        deleted = UpgradePlanner().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 2)
        assert(deleted[0] == building1)
        assert(deleted[1] == building3)

        var i = 6
        while (deleted != null && i++ < 2000) {
            println(deleted.size.toString() + " " + deleted)
            deleted = UpgradePlanner().findNextToDelete(state = state, deleted = deleted)
        }
        Assertions.assertEquals(5 + 10 + 10 + 5 + 1, i)

    }
}