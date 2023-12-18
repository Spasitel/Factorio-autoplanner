package ru.spasitel.factorioautoplanner.planner

import com.google.gson.Gson
import ru.spasitel.factorioautoplanner.data.Cell
import ru.spasitel.factorioautoplanner.data.Field
import ru.spasitel.factorioautoplanner.data.ProcessedItem
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.auto.BlueprintDTO
import ru.spasitel.factorioautoplanner.data.building.Building
import ru.spasitel.factorioautoplanner.formatter.BluePrintFieldExtractor
import ru.spasitel.factorioautoplanner.formatter.Formatter
import java.util.*

class GlobalPlanner {


    fun planeGlobal(recipeTree: Map<String, ProcessedItem>, fieldBlueprint: String) {
        val decodeField = Formatter.decode(fieldBlueprint)
        val dto = Gson().fromJson(decodeField, BlueprintDTO::class.java)
        val field = BluePrintFieldExtractor().transformBlueprintToField(dto)

        var double = true
        var min = 0.0
        var max = 1.0
        var best: State? = null
        while (max - min > 0.05) {
            val mid = (max + min) / 2
            val greedy = planeGreedy(recipeTree, field, mid)
            if (greedy != null) {
                min = calculateScore(greedy, recipeTree)
                best = greedy
                if (double) {
                    max = min * 3
                }
            } else {
                double = false
                max = mid
            }
        }

        if (best == null) {
            println("No solution found")
            return
        }
        val upgrade = planeUpgrade(recipeTree, field, best)

        planeDowngrade(upgrade)

    }


    private fun planeGreedy(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        var state = planeSpecial(recipeTree, field, mid) ?: return null

        val done =
            mutableSetOf("processing-unit", "electronic-circuit", "battery", "plastic-bar", "electric-engine-unit")
        while (recipeTree.keys.minus(done).isNotEmpty()) {
            val item = calculateNextItem(recipeTree, done)
            state = planeGreedyItem(recipeTree, item, state, field, mid) ?: return null
            state = planeRoboports(state, field) ?: return null
            done.add(item)
        }
        return state
    }

    private fun planeSpecial(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        val processors = planeProcessingUnit(recipeTree, field, mid) ?: return null
        val circuits = planeCircuits(processors, recipeTree, field, mid) ?: return null
        val batteries = planeBatteries(circuits, recipeTree, field, mid) ?: return null
        val plastic = planePlastic(batteries, recipeTree, field, mid) ?: return null
        val engines = planeEngines(plastic, recipeTree, field, mid) ?: return null
        return engines
    }

    private fun planeProcessingUnit(recipeTree: Map<String, ProcessedItem>, field: Field, mid: Double): State? {
        var score = 0.0
        var current = field.state
        while (score < mid) {
            var best: State? = current
            if (score > 0) {
                best = planeBeacon(current, field, "processing-unit")
            }
            val start = placeForItem(current, field, "processing-unit") ?: return best
            val processors = buildingsForItem(recipeTree, "processing-unit", start)
            val procWithConnections = TreeSet<State> { a, b -> compareForUnit(a, b, "processing-unit") }
            processors.forEach { proc ->
                val planeLiquid = planeLiquid(current, field, proc, "petroleum-gas")
                // add chest and inserters


            }// sort by score

            //add green circuits, sort by score

            //add copper wire


        }
        TODO()

    }

    private fun placeForItem(current: State, field: Field, unit: String): Cell? {


        TODO("Not yet implemented")
    }

    private fun compareForUnit(a: State, b: State, unit: String): Int {
        TODO()
    }

    private fun planeLiquid(current: State, field: Field, proc: Building, liquid: String): State? {
        TODO("Not yet implemented")
    }

    private fun buildingsForItem(recipeTree: Map<String, ProcessedItem>, unit: String, start: Cell): List<Building> {
        TODO("Not yet implemented")
    }


    private fun planeBeacon(current: State, field: Field, unit: String): State? {
        TODO("Not yet implemented")
    }

    private fun planeEngines(
        plastic: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        mid: Double
    ): State? {
        TODO()
    }

    private fun planePlastic(
        batteries: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        mid: Double
    ): State? {
        TODO()
    }

    private fun planeBatteries(
        circuits: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        mid: Double
    ): State? {
        TODO()
    }

    private fun planeCircuits(
        processors: State,
        recipeTree: Map<String, ProcessedItem>,
        field: Field,
        mid: Double
    ): State? {
        TODO()
    }


    private fun calculateNextItem(recipeTree: Map<String, ProcessedItem>, done: Set<String>): String {
        TODO("Not yet implemented")
    }

    private fun planeGreedyItem(
        recipeTree: Map<String, ProcessedItem>,
        item: String,
        state: State,
        field: Field,
        mid: Double
    ): State? {
        TODO("Not yet implemented")
    }

    private fun planeRoboports(state: State, field: Field): State? {
        TODO("Not yet implemented")
    }

    private fun calculateScore(greedy: State, recipeTree: Map<String, ProcessedItem>): Double {
        TODO("Not yet implemented")
    }

    private fun planeUpgrade(recipeTree: Map<String, ProcessedItem>, field: Field, best: State): State {
        TODO("Not yet implemented")
    }

    private fun planeDowngrade(upgrade: State) {
        TODO("Not yet implemented")
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val tree = TechnologyTreePlanner.scienceRoundTree()
            val amountMap = TreeMap<Double, Pair<String, String>>()
            tree.forEach { (key, value) ->
                value.ingredients.filter { it.key !in setOf("petroleum-gas", "lubricant", "sulfuric-acid") }
                    .forEach { (ingredient, amount) ->
                        var amount1 = amount
                        while (amountMap[amount1] != null) {

                            amount1 += 0.0001
                        }
                        amountMap[amount1] = Pair(key, ingredient)

                    }
            }
            println(amountMap)

            GlobalPlanner().planeGlobal(tree, BluePrintFieldExtractor.FIELD_3)
        }
    }
}