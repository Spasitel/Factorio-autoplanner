package ru.spasitel.factorioautoplanner.formatter

import com.google.gson.Gson
import org.junit.jupiter.api.Test
import ru.spasitel.factorioautoplanner.data.Place
import ru.spasitel.factorioautoplanner.data.Sell
import ru.spasitel.factorioautoplanner.data.State
import ru.spasitel.factorioautoplanner.data.Utils
import ru.spasitel.factorioautoplanner.data.auto.BlueprintDTO
import ru.spasitel.factorioautoplanner.data.building.BuildingType
import ru.spasitel.factorioautoplanner.data.building.Inserter

class BluePrintFieldExtractorTest {
    @Test
    fun testRails() {
        var state = State(emptySet(), emptyMap(), Sell(100, 20))
        for (i in 1..7 step 2) {
            val start = Sell(i * 10, 11)
            state = state.addBuilding(Inserter(Place(setOf(start), start), i))!!
            state = state.addBuilding(Utils.getBuilding(start.up().up(), BuildingType.PROVIDER_CHEST))!!
            state = state.addBuilding(Utils.getBuilding(start.up().right(), BuildingType.PROVIDER_CHEST))!!
            state = state.addBuilding(Utils.getBuilding(start.up().left(), BuildingType.PROVIDER_CHEST))!!
            state = state.addBuilding(Utils.getBuilding(start.right().right(), BuildingType.PROVIDER_CHEST))!!
            state = state.addBuilding(Utils.getBuilding(start.left().left(), BuildingType.PROVIDER_CHEST))!!
            state = state.addBuilding(Utils.getBuilding(start.down().right(), BuildingType.PROVIDER_CHEST))!!
            state = state.addBuilding(Utils.getBuilding(start.down().left(), BuildingType.PROVIDER_CHEST))!!
            state = state.addBuilding(Utils.getBuilding(start.down().down(), BuildingType.PROVIDER_CHEST))!!
        }

        var bCount = 1
        var json = Utils.START_JSON
        for (b in state.buildings) {
            var toJson = b.toJson(bCount)
            if (b.type == BuildingType.INSERTER) {
                toJson = toJson.replace("fast-inserter", "straight-rail")
                toJson = toJson.replace(".5", "")

            }
            json += toJson
            bCount++
        }
        json = json.substring(0, json.length - 1)
        json += Utils.END_JSON
        println(Formatter.encode(json))
    }

    @Test
    fun railsTest() {
        val blue =
            "0eNqdk11ugzAQhO+yzyYqGGPwVaoqImRFVgIb2U5VhHz3GKKmlQpKyZt/dr5ZjdcTnLorDpa0BzUBNUY7UO8TOGp13c1nfhwQFJDHHhjoup93tqYOAgPSZ/wClYYPBqg9ecK7ftmMR33tT2hjwUPpfNS2F58sCAaDcVFl9GwVSYl8qxiMcVFlIhqcyWJzv5eB/eFmD25nWnKemqS5oPOJ88bWLa4bHMS3hTyIsILlr2DzH2yxjs33Y4uqfNqt+He4RbUVbrrCLV5plz9NQe6YBbHRLl/hljti4BtcMQ/yMurq189g8InWLQVZmeYyvoQsecl5EcINaPEPDQ=="
        val decode = Formatter.decode(blue)
        val dto = Gson().fromJson(decode, BlueprintDTO::class.java)

        val rails = dto.blueprint.entities.filter { it.name == "straight-rail" }.sortedBy { it.position.x }
        val chests = dto.blueprint.entities.filter { it.name == "logistic-chest-storage" }.sortedBy { it.position.x }

        for (i in 0..3) {
            val rail = rails[i]
            val chest = chests[i]
            val x = rail.position.x
            val y = rail.position.y
            val direction = rail.direction
            val chestX = chest.position.x - x
            val chestY = chest.position.y - y
            println("$direction, $x, $y:  $chestX, $chestY")
        }
    }
}