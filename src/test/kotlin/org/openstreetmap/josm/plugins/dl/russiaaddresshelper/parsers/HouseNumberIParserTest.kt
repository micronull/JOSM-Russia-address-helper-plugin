package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags

internal class HouseNumberIParserTest {
    private val parser: HouseNumberParser = HouseNumberParser()

    @Test
    fun parseTest() {
        val ds = DataSet()
        val fakeCoordinate = EastNorth(0.0, 0.0)

        val test1 = parser.parse(
            "Калужская область, г Калуга, д Пучково, пер Совхозный, д б/н (инв.№ 37284)",
            fakeCoordinate,
            ds
        )
        assertEquals("", test1.houseNumber)
        assertTrue(test1.flags.contains(ParsingFlags.HOUSENUMBER_TOO_BIG))
        assertEquals(
            "", parser.parse(
                "Российская Федерация, Брянская область, городской округ город Брянск, город Брянск, улица 9 Января",
                fakeCoordinate,
                ds
            ).houseNumber
        )

        // assertEquals("", testSample.parse("Калужская область, г. Калуга, проезд Академический 3-й", fakeCoordinateds).houseNumber)
        //обл. Брянская, г. Брянск, пер. Белорусский, дом 30, корпус A - вообще непонятно, валидный ли это номер? для частного здания это явно ненормально

    }

    @ParameterizedTest
    @MethodSource("addresses")
    fun testHouseNumbers(address: String, expectedHousenumber: String?, expectedFlats: String?) {
        val ds = DataSet()
        val fakeCoordinate = EastNorth(0.0, 0.0)
        val parsingResult = parser.parse(address, fakeCoordinate, ds)
        if (expectedHousenumber != null) {
            assertEquals(expectedHousenumber, parsingResult.houseNumber)
        }
        assertEquals(expectedFlats, parsingResult.flats)
        //assertEquals(e, parsingResult.)

    }

    companion object {
        @JvmStatic
        fun addresses() = listOf(
            Arguments.of("Калужская область, г Калуга, ул. Болдина, д. 67, к. 2", "67 к2", ""),
            Arguments.of("обл. Свердловская, г. Дегтярск, ул. Шуры Екимовой, дом 49", "49", ""),
            Arguments.of("Красноярский край, г.Минусинск, ул. Абаканская, 51, пом. 6", "51", ""),
            Arguments.of("Красноярский край, г. Минусинск, ул. Штабная, 13-1", "13", "1"),
            Arguments.of("Красноярский край, г. Минусинск, ул. Канская, 41- 1,2,3,4", "41", "1,2,3,4"),
            Arguments.of("Красноярский край, г. Минусинск, ул Свердлова,   36а, строение 3", "36А с3", ""),
            Arguments.of("Красноярский край, г. Ачинск, ул. Кирова, 89А, корпус 2", "89А к2", ""),
            Arguments.of("Красноярский край, город Ачинск, переулок Трудовой, №58, корпус 2", "58 к2", ""),
            Arguments.of("Калужская область, Боровский р-н, д. Кабицыно, ул. А.Кабаевой, д. 7Б, корп. 2", "7Б к2", ""),
            Arguments.of("Красноярский край, Минусинский район, с. Большая Иня, ул. Мира, 60-б", "60Б", ""),
            Arguments.of("Калужская область, р-н Боровский, г Ермолино, ул 1 Мая, д 88 «А»", "88А", ""),
            Arguments.of("Калужская обл., р-н Сухиничский, г. Сухиничи, ул. Добролюбова, дом 47-\"а\"", "47А", ""),
            Arguments.of("Республика Башкортостан, Калтасинский район, с. Краснохолмский ул. Лесная д. 24", "24", ""),
            Arguments.of(
                "Российская Федерация, Калужская область, Боровский муниципальный район, городское поселение город Боровск, г Боровск, микрорайон Роща, ул Каманина, д 31, строен 2",
                "31 с2",
                ""
            ),
            Arguments.of(
                "Российская Федерация, Калужская область, Дзержинский район, сельское поселение \" Село Совхоз им. Ленина\", с. Совхоз им. Ленина, улица Верхняя, дом 19, сооружение 1",
                "19 с1",
                ""
            ),
            Arguments.of("Калужская область, г. Калуга, д. Пучково, ул. Совхозная, д. 5, часть №1", "5", ""),
            Arguments.of("обл. Брянская, г. Брянск, ул. Полесская, дом 16, А", "16А", ""),
            Arguments.of("обл. Брянская, г. Брянск, ул. Самарская, дом 9, Б, квартира №2", "9Б", "2"),
            Arguments.of(
                "Свердловская область, Полевской городской округ, город Полевской, микрорайон Березовая роща, дом 195/4",
                "195/4",
                ""
            ),
            Arguments.of("Калужская область, г Калуга, д Колюпаново, проезд Родниковый 4-й, д 5", "5", ""),
            Arguments.of(
                "обл. Ленинградская, р-н Приозерский, с/пос. Сосновское, массив Орехово-Северное, ДПК Светлана, 3-ий Цветочный переулок, дом 16, уч. № 306",
                "16",
                ""
            ),
            Arguments.of("обл. Московская р-н Мытищинский Сухаревский с.о. д. Шолохово д. 35", "35", ""),
            Arguments.of("Калужская обл., р-н Медынский, г. Медынь, ул. Митрофанова, д. 38, кв 2", "38", "2"),
            Arguments.of("обл. Калужская, р-н Жуковский, д. Верховье,, дом № 6, квартира № 2", "6", "2"),
            Arguments.of(
                "Российская Федерация, Московская область, Дмитровский городской округ, рабочий посёлок Некрасовский, ул. Южная, дом №15А, строение №1",
                "15А с1",
                ""
            ),
            Arguments.of("обл. Калужская, р-н Жуковский, г. Жуков, ул. Прудная, дом 2 кв1", "2", "1"),
            Arguments.of("обл. Калужская, р-н Жуковский, г. Жуков, ул. М.Горького, дом № 77, квартира № 2", "77", "2"),
            Arguments.of(
                "Калужская обл., р-н Спас-Деменский, г. Спас-Деменск, ул. Советская, д. 102, кор.2",
                "102 к2",
                ""
            ),
            Arguments.of("Калужская обл, р-н Спас-Деменский, г Спас-Деменск, ул Советская, д 3, корп 1", "3 к1", ""),
            Arguments.of("Калужская обл., р-н Мосальский, г. Мосальск, ул. Кирова, д. 34, помещение № 2", "34", ""),
            Arguments.of("Калужская обл., р-н Мосальский, г. Мосальск, ул. Гагарина, дом 10 \"Г\", кв. 1", "10Г", "1"),
            Arguments.of(
                "249930 Калужская область, Мосальский р-н, г Мосальск, ул Верхний Кавказ, д 19, блок 2",
                "19",
                ""
            ),
            Arguments.of(
                "Калужская обл., р-н Спас-Деменский, г. Спас-Деменск, ул. Механизаторов, д. 2 \"Б\", кв. 1",
                "2Б",
                "1"
            ),
            Arguments.of("Калужская область, г. Калуга, ул. Болдина, 89, к. 7", "89 к7", ""),
            Arguments.of(
                "Российская Федерация, Красноярский край, Ачинский район, п. Горный, ул. Садовая, дом 7, блок №1",
                "7",
                ""
            ),

            Arguments.of(
                "улица Пупкина, дом 15А/14Б, кв2",
                "15А",
                "2"
            ),

            //Arguments.of("", "6", "2"),
        )
    }
}