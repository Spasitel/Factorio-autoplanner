package ru.spasitel.factorioautoplanner.data.building

enum class BuildingType(var size: Int) {
    BEACON(3),
    INSERTER(1),
    REQUEST_CHEST(1),
    PROVIDER_CHEST(1),
    SMELTER(3),
    EMPTY(1),
    EMPTY2(2),
    EMPTY3(3),
    EMPTY4(4),
    OIL_REFINERY(5),

    PIPE(1),
    UNDERGROUND_PIPE(1),
    PUMP(2),
    STORAGE_TANK(3),

    ROCKET_SILO(9),
    ASSEMBLER(3),
    CHEMICAL_PLANT(3),
    LAB(3),

}