package ru.spasitel.factorioautoplanner.simple

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.spasitel.factorioautoplanner.simple.Main.isInteract
import ru.spasitel.factorioautoplanner.simple.building.Beacon
import ru.spasitel.factorioautoplanner.simple.building.Smelter

internal class MainTest {
    @Test
    fun intersectTest() {
        Assertions.assertTrue(
            isInteract(
                Smelter(1, 0),
                Smelter(0, 1)
            )
        )
        Assertions.assertFalse(
            isInteract(
                Smelter(0, 1),
                Smelter(0, 4)
            )
        )
        for (i in 1..5) {
            for (k in 1..5) {
                Assertions.assertTrue(
                    isInteract(
                        Smelter(3, 3),
                        Smelter(i, k)
                    )
                )
                Assertions.assertTrue(
                    isInteract(
                        Beacon(3, 3),
                        Smelter(i, k)
                    )
                )
                Assertions.assertTrue(
                    isInteract(
                        Smelter(3, 3),
                        Beacon(i, k)
                    )
                )
                Assertions.assertTrue(
                    isInteract(
                        Beacon(3, 3),
                        Beacon(i, k)
                    )
                )
            }
        }
        Assertions.assertTrue(
            isInteract(
                Smelter(3, 3),
                Smelter(6, 1)
            )
        )
    }
}