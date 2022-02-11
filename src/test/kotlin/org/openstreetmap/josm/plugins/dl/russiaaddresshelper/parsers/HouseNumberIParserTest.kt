package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.Test
import org.junit.jupiter.api.Assertions.*

internal class HouseNumberIParserTest {
    private val testSample: HouseNumberParser = HouseNumberParser()

    @Test fun parseTest() {
        assertEquals("49", testSample.parse("обл. Свердловская, г. Дегтярск, ул. Шуры Екимовой, дом 49"))
        assertEquals("23 с1", testSample.parse("Красноярский край, г. Минусинск, ул. Чайковского, 23, строение 1"))
        assertEquals("51", testSample.parse("Красноярский край, г.Минусинск, ул. Абаканская, 51, пом. 6"))
        assertEquals("13", testSample.parse("Красноярский край, г. Минусинск, ул. Штабная, 13-1,2"))
        assertEquals("41", testSample.parse("Красноярский край, г. Минусинск, ул. Канская, 41-1,2,3,4"))
        assertEquals("36А с3", testSample.parse("Красноярский край, г. Минусинск, ул Свердлова,   36а, строение 3"))
    }
}