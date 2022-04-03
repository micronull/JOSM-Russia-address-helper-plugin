package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.Test
import org.junit.jupiter.api.Assertions.*

internal class HouseNumberIParserTest {
    private val testSample: HouseNumberParser = HouseNumberParser()

    @Test fun parseTest() {
        assertEquals("49", testSample.parse("обл. Свердловская, г. Дегтярск, ул. Шуры Екимовой, дом 49").housenumber)
        assertEquals("23 с1", testSample.parse("Красноярский край, г. Минусинск, ул. Чайковского, 23, строение 1").housenumber)
        assertEquals("51", testSample.parse("Красноярский край, г.Минусинск, ул. Абаканская, 51, пом. 6").housenumber)
        assertEquals("13", testSample.parse("Красноярский край, г. Минусинск, ул. Штабная, 13-1").housenumber)
        assertEquals("1", testSample.parse("Красноярский край, г. Минусинск, ул. Штабная, 13-1").flats)
        assertEquals("41", testSample.parse("Красноярский край, г. Минусинск, ул. Канская, 41-1,2,3,4").housenumber)
        assertEquals("1,2,3,4", testSample.parse("Красноярский край, г. Минусинск, ул. Канская, 41-1,2,3,4").flats)
        assertEquals("36А с3", testSample.parse("Красноярский край, г. Минусинск, ул Свердлова,   36а, строение 3").housenumber)
    }
}