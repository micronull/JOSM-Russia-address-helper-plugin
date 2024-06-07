package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.openstreetmap.josm.JOSMFixture
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.AddressParts
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.PlaceTypes

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class AddressParserTest {


    private var ds: DataSet? = null

    @BeforeAll
    fun setUpBeforeClass() {
        JOSMFixture("src/test/config/unit-josm.home").init()
        ds = getDataset()
    }

    @Test
    fun parseTest() {
        if (ds == null) {return}
        val ds1 = ds!!
        val addressParser = AddressParser()
        var egrnAddress =
            """обл. Калужская, р-н Жуковский, МО Сельское поселение деревня Верховье, с/т "Коттедж",, уч 23"""
        var parsedAddress = addressParser.parse(egrnAddress, EastNorth.ZERO, ds1)
        assertEquals("Верховье", parsedAddress.parsedPlace.name)
      //  assertTrue(parsedAddress.flags.contains(ParsingFlags.UNSUPPORTED_ADDRESS_TYPE))

        val parsedAddress2 = addressParser.parse("Российская Федерация, Калужская область, Дзержинский район, сельское поселение \" Село Совхоз им. Ленина\", с. Совхоз им. Ленина, улица Верхняя, дом 19, сооружение 1", EastNorth.ZERO, ds1)

        egrnAddress ="""Российская Федерация, Красноярский край, Городской округ город Ачинск, г. Ачинск, мкр. 1-й"""
        parsedAddress = addressParser.parse(egrnAddress, EastNorth.ZERO, ds1)
        assertTrue(parsedAddress.flags.containsAll(listOf(ParsingFlags.PLACE_HAS_NUMBERED_NAME, ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED)))

        egrnAddress = "Калужская область, р-н. Мосальский, г. Мосальск, ул. Ломоносова, д. 65, кв. квартира 2,"
        val parsedAddress3 = addressParser.parse(egrnAddress, EastNorth.ZERO, ds1)
        assertEquals("65", parsedAddress3.getOsmAddress().housenumber)
        assertEquals("2", parsedAddress3.getOsmAddress().flatnumber)

        egrnAddress = "Российская Федерация, Московская область, городской округ Дмитровский, поселок Некрасовский, пер. 5-ый"
        val parsedAddress4 = addressParser.parse(egrnAddress, EastNorth.ZERO, ds1)
        assertEquals("", parsedAddress4.getOsmAddress().housenumber)
        assertTrue(parsedAddress4.flags.containsAll(listOf(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED)))


    }

    @ParameterizedTest
    @MethodSource("addresses")
    fun parseAllTest(address: String,expectedPlaceName:String?,expectedStreetName:String?, expectedHousenumber: String?, expectedFlats: String?, flags : List<ParsingFlags>?) {

        val addressParser = AddressParser()
        val parsedAddress = addressParser.parse(address, EastNorth.ZERO, ds!!)

        if (expectedHousenumber != null) {
            assertEquals(expectedHousenumber, parsedAddress.parsedHouseNumber.houseNumber)
        }

        if (expectedFlats != null) {
            assertEquals(expectedFlats, parsedAddress.parsedHouseNumber.flats)
        }

        if (flags!=null) {
            assertTrue(parsedAddress.flags.containsAll(flags))
        }
    }

    private fun getDataset(): DataSet {
        val ds = DataSet()

        ds.addPrimitiveRecursive(getStreet("Верхняя улица"))
        ds.addPrimitiveRecursive(getStreet("улица Ломоносова"))
        ds.addPrimitive(getPlace("Верховье", "village"))
        ds.addPrimitive(getPlace("1-й микрорайон", "suburb"))
        ds.addPrimitive(getPlace("8-й квартал", "quarter"))
        return ds;
    }

    private fun getStreet(name:String,): Way {
        val node3 = Node(EastNorth.ZERO)
        val node2 = Node(EastNorth.ZERO)
        val way = Way()
        way.addNode(node3)
        way.addNode(node2)
        way.put("name", name)
        way.put("highway", "residential")
        return way
    }

    private fun getPlace(name:String, type:String): Node {
        val node1 = Node(EastNorth.ZERO)
        node1.put("name", name)
        node1.put("place", type)
        return node1
    }

    companion object {
        @JvmStatic
        fun addresses() = listOf(
            Arguments.of("Калужская область, г Калуга, ул. Болдина, д. 67, к. 2",null,null, "67 к2", "", null),
            Arguments.of("обл. Свердловская, г. Дегтярск, ул. Шуры Екимовой, дом 49",null,null, "49", "", null),
            Arguments.of("Красноярский край, г.Минусинск, ул. Абаканская, 51, пом. 6",null,null, "51", "", null),
            Arguments.of("Красноярский край, г. Минусинск, ул. Штабная, 13-1", "13",null,null, "1", null),
            Arguments.of("Красноярский край, г. Минусинск, ул. Канская, 41- 1,2,3,4",null,null, "41", "1,2,3,4", null),
            Arguments.of("Красноярский край, г. Минусинск, ул Свердлова,   36а, строение 3",null,null, "36А с3", "", null),
            Arguments.of("Красноярский край, г. Ачинск, ул. Кирова, 89А, корпус 2",null,null, "89А к2", "", null),
            Arguments.of("Красноярский край, город Ачинск, переулок Трудовой, №58, корпус 2",null,null, "58 к2", "", null),
            Arguments.of("Калужская область, Боровский р-н, д. Кабицыно, ул. А.Кабаевой, д. 7Б, корп. 2",null,null, "7Б к2", "", null),
            Arguments.of("Красноярский край, Минусинский район, с. Большая Иня, ул. Мира, 60-б",null,null, "60Б", "", null),
            Arguments.of("Калужская область, р-н Боровский, г Ермолино, ул 1 Мая, д 88 «А»",null,null, "88А", "", null),
            Arguments.of("Калужская обл., р-н Сухиничский, г. Сухиничи, ул. Добролюбова, дом 47-\"а\"",null,null, "47А", "", null),
            Arguments.of("Республика Башкортостан, Калтасинский район, с. Краснохолмский ул. Лесная д. 24",null,null, "24", "", null),
            Arguments.of(
                "Российская Федерация, Калужская область, Боровский муниципальный район, городское поселение город Боровск, г Боровск, микрорайон Роща, ул Каманина, д 31, строен 2", null,null,
                "31 с2",
                ""
                ,null),
            Arguments.of(
                "Российская Федерация, Калужская область, Дзержинский район, сельское поселение \" Село Совхоз им. Ленина\", с. Совхоз им. Ленина, улица Верхняя, дом 19, сооружение 1", null,null,
                "19 с1",
                ""
                ,null),
            Arguments.of("Калужская область, г. Калуга, д. Пучково, ул. Совхозная, д. 5, часть №1", null,null, "5", "", null),
            Arguments.of("обл. Брянская, г. Брянск, ул. Полесская, дом 16, А",  null,null,"16А", "", null),
            Arguments.of("обл. Брянская, г. Брянск, ул. Самарская, дом 9, Б, квартира №2", null,null, "9Б", "2", null),
            Arguments.of(
                "Свердловская область, Полевской городской округ, город Полевской, микрорайон Березовая роща, дом 195/4", null,null,
                "195/4",
                ""
                ,null),
            Arguments.of("Калужская область, г Калуга, д Колюпаново, проезд Родниковый 4-й, д 5", null,null, "5", "", null),
            Arguments.of(
                "обл. Ленинградская, р-н Приозерский, с/пос. Сосновское, массив Орехово-Северное, ДПК Светлана, 3-ий Цветочный переулок, дом 16, уч. № 306", null,null,
                "16",
                ""
                ,null),
            Arguments.of("обл. Московская р-н Мытищинский Сухаревский с.о. д. Шолохово д. 35", null,null, "35", "", null),
            Arguments.of("Калужская обл., р-н Медынский, г. Медынь, ул. Митрофанова, д. 38, кв 2", null,null, "38", "2", null),
            Arguments.of("обл. Калужская, р-н Жуковский, д. Верховье,, дом № 6, квартира № 2", null,null, "6", "2", null),
            Arguments.of(
                "Российская Федерация, Московская область, Дмитровский городской округ, рабочий посёлок Некрасовский, ул. Южная, дом №15А, строение №1", null,null,
                "15А с1",
                ""
                ,null),
            Arguments.of("обл. Калужская, р-н Жуковский, г. Жуков, ул. Прудная, дом 2 кв1", null,null, "2", "1", null),
            Arguments.of("обл. Калужская, р-н Жуковский, г. Жуков, ул. М.Горького, дом № 77, квартира № 2", null,null, "77", "2", null),
            Arguments.of(
                "Калужская обл., р-н Спас-Деменский, г. Спас-Деменск, ул. Советская, д. 102, кор.2", null,null,
                "102 к2",
                ""
                ,null),
            Arguments.of("Калужская обл, р-н Спас-Деменский, г Спас-Деменск, ул Советская, д 3, корп 1", null,null, "3 к1", "", null),
            Arguments.of("Калужская обл., р-н Мосальский, г. Мосальск, ул. Кирова, д. 34, помещение № 2", null,null, "34", "", null),
            Arguments.of("Калужская обл., р-н Мосальский, г. Мосальск, ул. Гагарина, дом 10 \"Г\", кв. 1", null,null, "10Г", "1", null),
            Arguments.of(
                "249930 Калужская область, Мосальский р-н, г Мосальск, ул Верхний Кавказ, д 19, блок 2", null,null,
                "19",
                ""
                ,null),
            Arguments.of(
                "Калужская обл., р-н Спас-Деменский, г. Спас-Деменск, ул. Механизаторов, д. 2 \"Б\", кв. 1", null,null,
                "2Б",
                "1"
                ,null),
            Arguments.of("Калужская область, г. Калуга, ул. Болдина, 89, к. 7", null,null, "89 к7", "", null),
            Arguments.of(
                "Российская Федерация, Красноярский край, Ачинский район, п. Горный, ул. Садовая, дом 7, блок №1", null,null,
                "7",
                ""
                ,null),
            Arguments.of("Красноярский край, Ачинский район, п. Малиновка, квартал 1, строение 3", null,null, "", "", null),
            Arguments.of("Российская Федерация, Красноярский край, Городской округ город Ачинск, г. Ачинск, мкр. 1-й", null,null, "", "", listOf(ParsingFlags.PLACE_HAS_NUMBERED_NAME, ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED)),
          //  Arguments.of("Красноярский край, Ачинский район, п. Малиновка, квартал 1, строение 3", null,null, "", "", null),
         //   Arguments.of("Красноярский край, Ачинский район, п. Малиновка, квартал 1, строение 3", null,null, "", "", null),

            Arguments.of("Самарская область, г. Самара, р-н. Красноглинский, п. Мехзавод, кв-л. 8-й, д. 8", "8-й квартал",null, "8", "", null),
        )
    }

}