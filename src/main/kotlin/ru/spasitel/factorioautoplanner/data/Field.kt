package ru.spasitel.factorioautoplanner.data

data class Field(
    val state: State,
    val roboportsField: Pair<Cell, Cell>,
    val chestField: Pair<Cell, Cell>,
//    val electricField: Pair<Cell, Cell>,
    val assemblersField: Pair<Cell, Cell>,
) {

}
