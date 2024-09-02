package ru.spasitel.factorioautoplanner.planner

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.building.BuildingType

class UpgradeManagerTest {

    @Test
    fun findNextToDelete() {

        var state = State(emptySet(), emptyMap(), Cell(18, 18))
        val building1 = Utils.getBuilding(Cell(1, 0), BuildingType.SMELTER, recipe = "iron-plate")
        val building2 = Utils.getBuilding(Cell(3, 6), BuildingType.SMELTER, recipe = "iron-plate")
        val building3 = Utils.getBuilding(Cell(4, 3), BuildingType.SMELTER, recipe = "iron-plate")
        val building4 = Utils.getBuilding(Cell(7, 2), BuildingType.SMELTER, recipe = "iron-plate")
        val building5 = Utils.getBuilding(Cell(7, 6), BuildingType.SMELTER, recipe = "iron-plate")
        state =
            state.addBuilding(building2)!!.addBuilding(building1)!!.addBuilding(building3)!!.addBuilding(building4)!!
                .addBuilding(building5)!!
        var deleted = UpgradeManager().findNextToDelete(state = state, deleted = emptyList())
        assert(deleted!!.size == 1)
        assert(deleted[0] == building1)
        deleted = UpgradeManager().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 1)
        assert(deleted[0] == building2)
        deleted = UpgradeManager().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 1)
        assert(deleted[0] == building3)

        deleted = UpgradeManager().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 1)
        assert(deleted[0] == building4)

        deleted = UpgradeManager().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 1)
        assert(deleted[0] == building5)

        deleted = UpgradeManager().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 2)
        assert(deleted[0] == building1)
        assert(deleted[1] == building2)

        deleted = UpgradeManager().findNextToDelete(state = state, deleted = deleted)
        assert(deleted!!.size == 2)
        assert(deleted[0] == building1)
        assert(deleted[1] == building3)

        var i = 6
        while (deleted != null && i++ < 2000) {
            println(deleted.size.toString() + " " + deleted)
            deleted = UpgradeManager().findNextToDelete(state = state, deleted = deleted)
        }
        Assertions.assertEquals(5 + 10 + 10 + 5 + 1, i)

    }
}