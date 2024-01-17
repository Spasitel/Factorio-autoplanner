package ru.spasitel.factorioautoplanner.simple

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.spasitel.factorioautoplanner.simple.ChestPlannerUtils.addMandatoryChests
import ru.spasitel.factorioautoplanner.simple.building.Beacon
import ru.spasitel.factorioautoplanner.simple.building.Smelter

internal class SteelChestPlannerUtilsTest {
    @Test
    fun testMandatory() {
        var st: State? = State(HashSet(), 0.0, 0, Array(SimpleMain.SIZE) { arrayOfNulls(SimpleMain.SIZE) })
        st = st!!.addBuilding(Beacon(0, 4))
        st = st!!.addBuilding(Smelter(0, 7))
        st = st!!.addBuilding(Smelter(0, 10))
        st = st!!.addBuilding(Beacon(3, 8))
        Assertions.assertNull(addMandatoryChests(st!!))
    }
}