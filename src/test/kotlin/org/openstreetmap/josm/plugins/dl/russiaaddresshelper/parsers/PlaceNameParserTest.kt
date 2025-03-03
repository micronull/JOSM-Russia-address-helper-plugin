package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openstreetmap.josm.JOSMFixture
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.PlaceTypes

internal class PlaceNameParserTest {
    private val placeTypes: PlaceTypes = PlaceTypes.byYml("/references/place_types.yml")


    @BeforeEach
    fun setUpBeforeClass() {
        JOSMFixture("src/test/config/unit-josm.home").init()
    }

    @Test
    fun parseTest() {
        val testData = mapOf(
            "Товарково" to ("Калужская обл., р-н Дзержинский, п. Товарково, дом 3" to "посёлок"),
            "Звёздный квартал" to ("Калужская область, г. Калуга, квартал Звездный, д. 39" to "квартал"),
            "Промышленный микрорайон" to ("Калужская обл., р-н Дзержинский, п. Товарково, мкр. Промышленный, дом 3" to "микрорайон"),
            "Шемякино" to ("Российская Федерация, Калужская область, Малоярославецкий муниципальный район, сельское поселение  \"Село Коллонтай\", д. Шемякино, ул. Привокзальная, здание 26 строение 2" to "деревня"),
            "Коллонтай" to ("Российская Федерация, Калужская область, Малоярославецкий муниципальный район, с. Коллонтай, ул. Привокзальная, здание 26 строение 2" to "село"),
            "В.И. Ленина" to ("Российская Федерация, Калужская область, Малоярославецкий муниципальный район, п. В.И. Ленина, ул. Привокзальная, здание 26 строение 2" to "посёлок"),
            "3-й микрорайон" to ("Калужская обл., р-н Дзержинский, п. Товарково, 3 микрорайон, дом 3" to "микрорайон"),
            "3-й Виртуальный микрорайон" to ("Калужская обл., р-н Дзержинский, п. Товарково, мкр. 3 Виртуальный, дом 3" to "микрорайон"),
            "2-й Виртуальный микрорайон" to ("Калужская обл., р-н Дзержинский, п. Товарково, 2 Виртуальный мкр., дом 3" to "микрорайон"),
            "Пупино-Лупино" to ("Калужская обл., р-н Дзержинский, село Пупино-Лупино, дом 3" to "село"),
            "Кривошеино" to ("Российская Федерация, Калужская область,  Жуковский муниципальный район, сельское поселение деревня Верховье, д. Кривошеино, земельный участок 91" to "деревня"),
            "квартал Политехникума" to ("Красноярский край, г. Ачинск, кв-л Политехникума, № 2" to "квартал"),
            "квартал 28" to ("Красноярский край, г. Ачинск, 28 кв-л, №1" to "квартал"),
            "микрорайон Авиатор" to ("Ачинск, мкр. \"Авиатор\", 56" to "микрорайон"),
            "посёлок Птицефабрики" to ("Ачинск, п. Птицефабрики, 56" to "посёлок"),
            "Аникановский" to ("Российская Федерация, Калужская область, Бабынинский р-н, хутор Аникановский, д 8" to "хутор"),
            "Аникановский" to ("Калужская область, р-н Бабынинский, х. Аникановский, д. 10" to "хутор"),
            "Бабынинское отделение" to ("Калужская область, Бабынинский район, п. Бабынинское Отделение, д. 35А" to "посёлок"),
            "Голубино" to ("Российская Федерация, Архангельская область, м.р-н Пинежский, с.п. Пинежское, п. Голубино, д. 1Б" to "посёлок")


        )
        val primitivesToCompare: MutableMap<String, Map<String, List<OsmPrimitive>>> = mutableMapOf()
        placeTypes.types.forEach { type ->

            val foundPrimitives = testData.entries.filter { it.value.second == type.name }
                .associate { Pair(it.key, listOf<OsmPrimitive>(getNewNode(it.key))) }
            primitivesToCompare.putIfAbsent(type.name, foundPrimitives)
        }

        testData.forEach { (osmPlace, egrnPlace) ->
            assertEquals(
                osmPlace,
                ParsedPlace.identify(egrnPlace.first, placeTypes, primitivesToCompare.toMutableMap()).name
            )
        }
    }

    private fun getNewNode(name: String): Node {
        val node = Node(EastNorth.ZERO)
        node.put("name", name)
        return node
    }
}