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
        assertEquals("1", testSample.parse("Красноярский край, г. Минусинск, ул. Штабная, 13 - 1").flats)
        assertEquals("41", testSample.parse("Красноярский край, г. Минусинск, ул. Канская, 41- 1,2,3,4").housenumber)
        assertEquals("1,2,3,4", testSample.parse("Красноярский край, г. Минусинск, ул. Канская, 41-1,2,3,4").flats)
        assertEquals("36А с3", testSample.parse("Красноярский край, г. Минусинск, ул Свердлова,   36а, строение 3").housenumber)

        assertEquals("89А к2", testSample.parse("Красноярский край, г. Ачинск, ул. Кирова, 89А, корпус 2").housenumber)
        assertEquals("58 к2", testSample.parse("Красноярский край, город Ачинск, переулок Трудовой, №58, корпус 2").housenumber)
        assertEquals("7Б к2", testSample.parse("Калужская область, Боровский р-н, д. Кабицыно, ул. А.Кабаевой, д. 7Б, корп. 2").housenumber)
        assertEquals("60Б", testSample.parse("Красноярский край, Минусинский район, с. Большая Иня, ул. Мира, 60-б").housenumber)
        assertEquals("88А", testSample.parse("Калужская область, р-н Боровский, г Ермолино, ул 1 Мая, д 88 «А»").housenumber)
        assertEquals("47А", testSample.parse("Калужская обл., р-н Сухиничский, г. Сухиничи, ул. Добролюбова, дом 47-\"а\"").housenumber)
        assertEquals("24", testSample.parse("Республика Башкортостан, Калтасинский район, с. Краснохолмский ул. Лесная д. 24").housenumber)

        assertEquals("31 с2", testSample.parse("Российская Федерация, Калужская область, Боровский муниципальный район, городское поселение город Боровск, г Боровск, микрорайон Роща, ул Каманина, д 31, строен 2").housenumber)
        assertEquals("56", testSample.parse("Калужская область, г. Калуга, ул. Николая Островского, д. 56, часть 1").housenumber)


    }
}