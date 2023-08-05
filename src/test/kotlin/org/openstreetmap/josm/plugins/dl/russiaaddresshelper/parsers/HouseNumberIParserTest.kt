package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags

internal class HouseNumberIParserTest {
    private val testSample: HouseNumberParser = HouseNumberParser()

    @Test fun parseTest() {
        val fakeCoordinate =  EastNorth(0.0, 0.0)
        assertEquals("49", testSample.parse("обл. Свердловская, г. Дегтярск, ул. Шуры Екимовой, дом 49", fakeCoordinate).housenumber)
        assertEquals("23 с1", testSample.parse("Красноярский край, г. Минусинск, ул. Чайковского, 23, строение 1",fakeCoordinate).housenumber)
        assertEquals("51", testSample.parse("Красноярский край, г.Минусинск, ул. Абаканская, 51, пом. 6",fakeCoordinate).housenumber)
        assertEquals("13", testSample.parse("Красноярский край, г. Минусинск, ул. Штабная, 13-1",fakeCoordinate).housenumber)
        assertEquals("1", testSample.parse("Красноярский край, г. Минусинск, ул. Штабная, 13 - 1",fakeCoordinate).flats)
        assertEquals("41", testSample.parse("Красноярский край, г. Минусинск, ул. Канская, 41- 1,2,3,4",fakeCoordinate).housenumber)
        assertEquals("1,2,3,4", testSample.parse("Красноярский край, г. Минусинск, ул. Канская, 41-1,2,3,4",fakeCoordinate).flats)
        assertEquals("36А с3", testSample.parse("Красноярский край, г. Минусинск, ул Свердлова,   36а, строение 3",fakeCoordinate).housenumber)

        assertEquals("89А к2", testSample.parse("Красноярский край, г. Ачинск, ул. Кирова, 89А, корпус 2",fakeCoordinate).housenumber)
        assertEquals("58 к2", testSample.parse("Красноярский край, город Ачинск, переулок Трудовой, №58, корпус 2",fakeCoordinate).housenumber)
        assertEquals("7Б к2", testSample.parse("Калужская область, Боровский р-н, д. Кабицыно, ул. А.Кабаевой, д. 7Б, корп. 2",fakeCoordinate).housenumber)
        assertEquals("60Б", testSample.parse("Красноярский край, Минусинский район, с. Большая Иня, ул. Мира, 60-б",fakeCoordinate).housenumber)
        assertEquals("88А", testSample.parse("Калужская область, р-н Боровский, г Ермолино, ул 1 Мая, д 88 «А»",fakeCoordinate).housenumber)
        assertEquals("47А", testSample.parse("Калужская обл., р-н Сухиничский, г. Сухиничи, ул. Добролюбова, дом 47-\"а\"",fakeCoordinate).housenumber)
        assertEquals("24", testSample.parse("Республика Башкортостан, Калтасинский район, с. Краснохолмский ул. Лесная д. 24",fakeCoordinate).housenumber)

        assertEquals("31 с2", testSample.parse("Российская Федерация, Калужская область, Боровский муниципальный район, городское поселение город Боровск, г Боровск, микрорайон Роща, ул Каманина, д 31, строен 2",fakeCoordinate).housenumber)
        assertEquals("56", testSample.parse("Калужская область, г. Калуга, ул. Николая Островского, д. 56, часть 1",fakeCoordinate).housenumber)
        assertEquals("19 с1", testSample.parse("Российская Федерация, Калужская область, Дзержинский район, сельское поселение \" Село Совхоз им. Ленина\", с. Совхоз им. Ленина, улица Верхняя, дом 19, сооружение 1",fakeCoordinate).housenumber)
        assertEquals("5", testSample.parse("Калужская область, г. Калуга, д. Пучково, ул. Совхозная, д. 5, часть №1",fakeCoordinate).housenumber)
        assertEquals("16А", testSample.parse("обл. Брянская, г. Брянск, ул. Полесская, дом 16, А", fakeCoordinate).housenumber)
        assertEquals("9Б", testSample.parse("обл. Брянская, г. Брянск, ул. Самарская, дом 9, Б, квартира №2", fakeCoordinate).housenumber)

        val test1 = testSample.parse("Калужская область, г Калуга, д Пучково, пер Совхозный, д б/н (инв.№ 37284)",fakeCoordinate)
        assertEquals("", test1.housenumber)
        assertTrue(test1.flags.contains(ParsingFlags.HOUSENUMBER_TOO_BIG))
        assertEquals("", testSample.parse("Российская Федерация, Брянская область, городской округ город Брянск, город Брянск, улица 9 Января", fakeCoordinate).housenumber)
        assertEquals("195/4", testSample.parse("Свердловская область, Полевской городской округ, город Полевской, микрорайон Березовая роща, дом 195/4", fakeCoordinate).housenumber)
        assertEquals("16", testSample.parse("обл. Ленинградская, р-н Приозерский, с/пос. Сосновское, массив Орехово-Северное, ДПК Светлана, 3-ий Цветочный переулок, дом 16, уч. № 306", fakeCoordinate).housenumber)

        //обл. Брянская, г. Брянск, пер. Белорусский, дом 30, корпус A - вообще непонятно, валидный ли это номер? для частного здания это явно ненормально

    }
}