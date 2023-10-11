package ru.spasitel.factorioautoplanner.data

enum class BuildingType(var size: Int) {
    BEACON(3), INSERTER(1), INPUT_CHEST(1), OUTPUT_CHEST(1), SMELTER(3), ASSEMBLER(3)
}