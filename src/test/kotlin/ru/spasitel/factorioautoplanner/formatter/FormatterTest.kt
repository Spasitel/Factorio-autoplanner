package ru.spasitel.factorioautoplanner.formatter

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.spasitel.factorioautoplanner.data.auto.BlueprintDTO
import ru.spasitel.factorioautoplanner.formatter.Formatter.decode
import ru.spasitel.factorioautoplanner.formatter.Formatter.encode
import java.util.*


internal class FormatterTest {
    @Test
    fun testFormatter() {
        Assertions.assertEquals(decode(smelter), smelter_dec)
        Assertions.assertEquals(decode(smelter2), smelter2_dec)
        Assertions.assertEquals(
            encode(smelter_dec)!!.substring(4), smelter.substring(4)
        )
        Assertions.assertEquals(
            encode(smelter2_dec)!!.substring(4), smelter2.substring(4)
        )
    }

    @Test
    fun testReformatter() {
        val orig =
            "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"
        val decode = decode(orig)
        val repl = decode!!.replace("copper-plate", "stone")
        println(encode(repl))
    }

    @Test
    fun testReformatter2() {
        val orig =
            "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"
        val decode = decode(orig)
        val repl = decode!!.replace("copper-plate", "steel-plate")
        println(encode(repl))
    }

    @Test
    fun testReformatter3() {
        val orig =
            "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"
        val decode = decode(orig)!!.replace("copper-plate", "steel-plate")

        val gson = Gson()
        val mMineUserEntity = gson.fromJson(decode, BlueprintDTO::class.java)
        val set = TreeSet<Double>()
        for (entity in mMineUserEntity.blueprint!!.entities!!) {
            if (entity.name != "decider-combinator" && entity.name != "arithmetic-combinator") continue
            when (entity.position.x!!) {
                -635.0 -> entity.position.y += -5
                -637.0 -> entity.position.y += -5
                -639.0 -> entity.position.y += -5
                -641.0 -> entity.position.y += -5
                -643.0 -> entity.position.y += -5
                -645.0 -> entity.position.y += -5
//                -647.0 -> entity.position.y += -5
            }
            if (entity.position.x == -641.0 && entity.position.y == -997.5) {
                entity.position.y += -2
            }
            if (entity.position.x == -641.0 && entity.position.y == -996.5) {
                entity.position.y += -2
            }
            set.add(entity.position.y)
        }
        println(set)
        val repl = gson.toJson(mMineUserEntity)

        println(encode(repl))
    }

    @Test
    fun testDirections() {
        val decode = decode(refinery)!!
        val mMineUserEntity = Gson().fromJson(decode, BlueprintDTO::class.java)
        val normal = BluePrintFieldExtractor().normalizeBlueprint(mMineUserEntity)
        println(normal)
    }
    companion object {
        const val pipes =
            "0eNqd1u1ugyAUBuB7Ob+x4VPQW1mWxbakIZtoFJc1jfc+bZdFO8zB/RNDHg7wgt7g+DHYtnM+QHkDd2p8D+XLDXp38dXH/C5cWwsluGBrIOCrem71oemqi81C5d9hJOD82X5BycZXAtYHF5x9OPfG9c0P9dF2U4dfoXUTS6Bt+qlz4+eRJiDTTB8Ugev0yKiRBzWO5I/CcYXiikAVmuOKRBW+rEXEFYXPyOBKvm91NxSNKxJXDK4oXClwReAKo/sCs8UkpJclMHh8aZHA7Mwv32DkPmarGjzBNCF7DI8wTYgN0/GbKrZji6XWxQZn0jn1xBE4u86eHp14DF9kfKhb7NTqfE3K2P1IcZKvyBjCUGQe54dQKVPlHK9LbZN5jFyfgiw02aVrBn/GbmItkni5g19tk0haELWHl098DMz/C7LneuX8Lb9/9cvFTwKBT9v1jwkZJnXBtTbCCDFl6Bv6SZEU"
        const val refinery =
            "0eNq1lNuKgzAQQP9lnmPReEnqr5RlsTotAxoliWVF/PdNFJZlFdc++JbJTM4cQiYj3OseO03KQj4Cla0ykN9GMPRURe337NAh5EAWG2CgisZHLdWBxgcp1ANMDEhV+AV5NH0wQGXJEi6cORg+Vd/cUbuCbQKDrjXuUKt8RwcKBA8vKYPBLaNQXC+pa6KxpNmlqF6FKrEKPKTTbYnGkHo6jLc0nmE6dPmmrfoagxjyeJrYyoYftomSlU1Fzmcp4ie4xYfdQrnnlpzglhx343tu2Qlu6Y/bozA2IGVQW5f474mlq4vbgGfH4ZHcg2cbcPEGPPsD38DJN3DJniv3Qz2Pf/7rt2DwQm2WAhkl4sqFkLGM42yavgFTomiM"

        const val chests =
            "0eNqVkNEKwjAMRf8lz524zrnZXxGROcMMbG1p6nCM/rvtfBkiqG+5gZx7b2a49He0jrQHNQO1RjOo4wxMnW76tPOTRVBAHgcQoJshqd50xJ7arL0h+8w2zDRiZp0Z6YoOggDSV3yAyoP4l8beuKbDFUSGkwDUnjzhK98iprO+D5dop/JvLAHWcDw3OqWIyKySu00pYIpjvj3ITRlS0jeu/LnxJ4NyZVAkg9hiaa5WbxcwouPlTtb5rjrIqqqLuij2ITwB1MqKbw=="
        const val smelter =
            "0eNqVkttuhCAQht9lrrFRFG19lWazcXF2O4mCC2hqDO9e1NaeNE2vYMg/H/8cJrg0PXaGlINyApJaWSifJ7B0U1Uzv7mxQyiBHLbAQFXtHGGD0hmS0bU3qpIIngGpGl+hTPyJASpHjnBlLcF4Vn17QRMExxQGnbYhUav55wCLMi4eBIMxXPNw85794vGN1+gbWRd48gWti7rKWhow6oweqA7SPTzf8Pk+Pt3wpCwad8BJv3EY1GRCcYsk36FmR6YN3vtw/ulWLL+8q89XakLK2u6POXz6NlpF2sztlbqfJ83j2J92XIn/1ip+1srn6S+7Un5ZLQZDcLcKHpOseOJFWhQiiTPv3wDUrNG6"
        const val smelter2 =
            "0eNqFkdtuwyAMht/F11CVHEbLq1RTRYg7WUoIAlItinj3QiZtXbUpdxj7/z8fVuiGGZ0nG0GtQGayAdRlhUAfVg/lLy4OQQFFHIGB1WOJcEATPRl+m73VBiExINvjJyiR2K68Q51JT6IqvTNAGykSfjWwBcvVzmOHPru+ahm4KeTy/MyUYvFWnw4tgwUUF1I0h7bYZ2woBcEh9nyc+nlAXhdgafMFUv0/3184+YM7nn/jnM8kE+me7feo9Tf1pkPkZAP6mBN7E4qMLGvbVqueDsngjj5suuokGnmuZC1lK45NSg+/C6D6"
        const val smelter2_dec =
            "{\"blueprint\":{\"icons\":[{\"signal\":{\"type\":\"item\",\"name\":\"electric-furnace\"},\"index\":1},{\"signal\":{\"type\":\"item\",\"name\":\"beacon\"},\"index\":2}],\"entities\":[{\"entity_number\":1,\"name\":\"beacon\",\"position\":{\"x\":2638.5,\"y\":-1714.5},\"items\":{\"speed-module-3\":2}},{\"entity_number\":2,\"name\":\"electric-furnace\",\"position\":{\"x\":2637.5,\"y\":-1709.5},\"items\":{\"productivity-module-3\":2}},{\"entity_number\":3,\"name\":\"fast-inserter\",\"position\":{\"x\":2638.5,\"y\":-1711.5}}],\"item\":\"blueprint\",\"version\":281479273775104}}"
        const val smelter_dec =
            "{\"blueprint\":{\"icons\":[{\"signal\":{\"type\":\"item\",\"name\":\"electric-furnace\"},\"index\":1}],\"entities\":[{\"entity_number\":1,\"name\":\"electric-furnace\",\"position\":{\"x\":-425.5,\"y\":-65.5}},{\"entity_number\":2,\"name\":\"logistic-chest-passive-provider\",\"position\":{\"x\":-422.5,\"y\":-66.5}},{\"entity_number\":3,\"name\":\"inserter\",\"position\":{\"x\":-423.5,\"y\":-66.5},\"direction\":6},{\"entity_number\":4,\"name\":\"logistic-chest-requester\",\"position\":{\"x\":-422.5,\"y\":-65.5},\"request_filters\":[{\"index\":1,\"name\":\"iron-ore\",\"count\":200}]},{\"entity_number\":5,\"name\":\"inserter\",\"position\":{\"x\":-423.5,\"y\":-65.5},\"direction\":2}],\"item\":\"blueprint\",\"version\":281479273775104}}"
    }
}