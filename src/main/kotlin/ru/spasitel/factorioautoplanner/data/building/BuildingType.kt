package ru.spasitel.factorioautoplanner.data.building

enum class BuildingType(var size: Int) {
    BEACON(3), INSERTER(1), REQUEST_CHEST(1), PROVIDER_CHEST(1), SMELTER(3), ASSEMBLER(3), EMPTY(1), CHEMICAL_PLANT(3), ROCKET_SILO(
        9
    )
}