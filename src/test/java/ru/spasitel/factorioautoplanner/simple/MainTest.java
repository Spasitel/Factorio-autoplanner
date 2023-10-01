package ru.spasitel.factorioautoplanner.simple;

import org.junit.jupiter.api.Test;
import ru.spasitel.factorioautoplanner.simple.building.Beacon;
import ru.spasitel.factorioautoplanner.simple.building.Smelter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MainTest {

    @Test
    public void intersectTest() {
        assertTrue(Main.isInteract(
                new Smelter(1, 0),
                new Smelter(0, 1)));
        assertFalse(Main.isInteract(
                new Smelter(0, 1),
                new Smelter(0, 4)
        ));
        for (int i = 1; i < 6; i++) {
            for (int k = 1; k < 6; k++) {
                assertTrue(Main.isInteract(
                        new Smelter(3, 3),
                        new Smelter(i, k)));
                assertTrue(Main.isInteract(
                        new Beacon(3, 3),
                        new Smelter(i, k)));
                assertTrue(Main.isInteract(
                        new Smelter(3, 3),
                        new Beacon(i, k)));
                assertTrue(Main.isInteract(
                        new Beacon(3, 3),
                        new Beacon(i, k)));
            }
        }

        assertTrue(Main.isInteract(
                new Smelter(3, 3),
                new Smelter(6, 1)
        ));


    }
}