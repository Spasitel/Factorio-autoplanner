package ru.spasitel.factorioautoplanner.simple;

import org.junit.jupiter.api.Test;
import ru.spasitel.factorioautoplanner.simple.building.Beacon;
import ru.spasitel.factorioautoplanner.simple.building.Building;
import ru.spasitel.factorioautoplanner.simple.building.Smelter;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

class ChestPlannerUtilsTest {
    @Test
    public void testMandatory(){
        State st = new State(new HashSet<>(), 0, 0, new Building[Main.SIZE][Main.SIZE]);
        st = st.addBuilding(new Beacon(0,4 ));
        st = st.addBuilding(new Smelter(0,7 ));
        st = st.addBuilding(new Smelter(0,10 ));
        st = st.addBuilding(new Beacon(3,8 ));
        assertNull(ChestPlannerUtils.addMandatoryChests(st));
    }

}