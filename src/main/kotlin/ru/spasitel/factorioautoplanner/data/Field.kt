package ru.spasitel.factorioautoplanner.data

import ru.spasitel.factorioautoplanner.data.building.Chest

data class Field(
    val state: State,
    val chests: Map<String, Set<Chest>>,
    val liquids: Map<String, Set<Cell>>,
    val roboportsField: Pair<Cell, Cell>,
    val chestField: Pair<Cell, Cell>,
    val electricField: Pair<Cell, Cell>
) {

}
