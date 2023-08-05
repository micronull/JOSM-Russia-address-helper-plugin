package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.PlaceTypes

internal class PlaceNameParserTest {
    private val placeTypes: PlaceTypes = PlaceTypes.byYml("/references/place_types.yml")

    @Test
    fun parseTest() {
        val testData = mapOf(
            "Товарково" to ("Калужская обл., р-н Дзержинский, п. Товарково, дом 3" to "посёлок"),
            "Звёздный квартал" to ("Калужская область, г. Калуга, квартал Звездный, д. 39" to "квартал"),
            "Промышленный микрорайон" to ("Калужская обл., р-н Дзержинский, п. Товарково, мкр. Промышленный, дом 3" to "микрорайон"),
            "Шемякино" to ("Российская Федерация, Калужская область, Малоярославецкий муниципальный район, сельское поселение  \"Село Коллонтай\", д. Шемякино, ул. Привокзальная, здание 26 строение 2" to "деревня"),
            "Коллонтай" to ("Российская Федерация, Калужская область, Малоярославецкий муниципальный район, с. Коллонтай, ул. Привокзальная, здание 26 строение 2" to "село"),
            "В.И. Ленина" to ("Российская Федерация, Калужская область, Малоярославецкий муниципальный район, п. В.И. Ленина, ул. Привокзальная, здание 26 строение 2" to "посёлок")
        )
        val primitivesToCompare: MutableMap<String, Map<String, List<OsmPrimitive>>> = mutableMapOf()
        placeTypes.types.forEach { type ->

            val foundPrimitives = testData.entries.filter { it.value.second == type.name }
                .associate { Pair(it.key, listOf<OsmPrimitive>()) }
            primitivesToCompare.putIfAbsent(type.name, foundPrimitives)
        }

        testData.forEach { (osmPlace, egrnPlace) ->
            assertEquals(
                osmPlace,
                ParsedPlace.identify(egrnPlace.first, placeTypes, primitivesToCompare.toMutableMap()).name
            )
        }
    }
}