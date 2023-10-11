package ru.spasitel.factorioautoplanner.data

data class Building(val place: Place, val type: BuildingType, val recipe: Recipe, val connected: Set<Building>) {
}
