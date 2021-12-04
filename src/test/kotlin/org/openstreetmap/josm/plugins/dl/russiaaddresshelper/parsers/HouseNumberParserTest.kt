package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

internal class HouseNumberParserTest {
    private val testSample: HouseNumberParser = HouseNumberParser()

    @Test fun parseTest() {
        assertEquals("49", testSample.parse("обл. Свердловская, г. Дегтярск, ул. Шуры Екимовой, дом 49"))
    }
}