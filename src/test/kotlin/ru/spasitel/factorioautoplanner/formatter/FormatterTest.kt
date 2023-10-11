package ru.spasitel.factorioautoplanner.formatter

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import ru.spasitel.factorioautoplanner.formatter.Formatter.decode
import ru.spasitel.factorioautoplanner.formatter.Formatter.encode

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

    companion object {
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