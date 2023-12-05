package ru.spasitel.factorioautoplanner.planner

import com.google.gson.Gson
import ru.spasitel.factorioautoplanner.data.Field
import ru.spasitel.factorioautoplanner.data.ProcessedItem
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.auto.BlueprintDTO
import ru.spasitel.factorioautoplanner.formatter.BluePrintFieldExtractor
import ru.spasitel.factorioautoplanner.formatter.Formatter

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
        var state = if (recipeTree["petroleum-gas"] != null && recipeTree["petroleum-gas"]!!.amount > 0.0) {
            OilPlanner().planeOil(recipeTree, field, mid) ?: return null
        } else {
            field.state
        }
        Utils.printBest(state)

        val done = HashSet<String>()
        while (recipeTree.keys.minus(done).isNotEmpty()) {
            val item = calculateNextItem(recipeTree, done)
            state = planeGreedyItem(recipeTree, item, state, field, mid) ?: return null
            state = planeRoboports(state, field) ?: return null
            done.add(item)
        }
        return state
    }

    private fun calculateNextItem(recipeTree: Map<String, ProcessedItem>, done: java.util.HashSet<String>): String {
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
            GlobalPlanner().planeGlobal(tree, BluePrintFieldExtractor.FIELD)
        }
    }
}