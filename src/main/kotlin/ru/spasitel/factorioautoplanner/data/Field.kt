package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.building.Chest

data class Field(
    val state: State,
    val chests: Map<String, Set<Chest>>,
    val liquids: Map<String, Set<Sell>>,
    val roboportsField: Pair<Sell, Sell>,
    val chestField: Pair<Sell, Sell>
) {

}
