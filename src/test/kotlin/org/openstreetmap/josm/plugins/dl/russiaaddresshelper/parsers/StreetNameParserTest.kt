package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.Test
import org.junit.jupiter.api.Assertions.*
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetTypes

internal class StreetNameParserTest {
    private val streetTypes: StreetTypes = StreetTypes.byYml("/references/street_types.yml")

    @Test
    fun parseTest() {
        val testData = mapOf(
            "Михалевская улица" to "Калужская область, г. Калуга, Михалевская ул., д. 39",
            "1-я Михалевская улица" to "Калужская область, г. Калуга, Михалевская 1-я ул., д. 39",
            "2-ая Михалевская улица" to "Калужская область, г. Калуга, 2-я Михалёвская ул., д. 39",
            "улица Шуры Екимовой" to "обл. Свердловская, г. Дегтярск, ул. Шуры Екимовой, дом 49",
            //в текущей регулярке нет возможности понять закончилась ли улица. Может делать отсечку по признаку дома?
            // "Лесная улица" to "Республика Башкортостан, Калтасинский район, с. Краснохолмский ул. Лесная д. 24",
            "улица Николая Островского" to "Калужская область, г. Калуга, ул. Н. Островского, д. 34, часть 2",
            "улица Гизатуллина" to "Республика Башкортостан, р-н. Калтасинский, с. Краснохолмский, ул. имени Гизатуллина, д. 10",
            "2-й переулок Победы" to "Калужская область, р-н Сухиничский, г. Сухиничи, пер. Победы 2-й, д. 19",
            "1-ый Садовый переулок" to "Калужская область, р-н Сухиничский, г. Сухиничи, пер. Садовый 1-й, д. 4",
            "2-й Садовый переулок" to "Калужская обл., р-н Сухиничский, г. Сухиничи,  2-й пер. Садовый, дом 22",
            "2-й переулок Ломоносова" to "Калужская область, р-н Сухиничский, г. Сухиничи, 2-й пер. Ломоносова , д. 3а",
            "2-я улица Буденного" to "Калужская область, р-н Сухиничский, г. Сухиничи, ул. 2-я Буденного, стр. 2",
            "1-я Смоленская улица" to "Калужская обл, р-н Сухиничский, г.Сухиничи, ул.Смоленская 1-я, дом 2",
            "1-й Ленинский переулок" to "Калужская обл., р-н Думиничский, п. Думиничи, 1-й Ленинский пер., д. 21",
            "1-я улица Буденного" to "Калужская область, р-н Сухиничский, г. Сухиничи, 1-я ул. Буденного, стр. 2",
            "Киёвский проезд" to "Российская Федерация, Калужская обл., г.о. \"Город Калуга\", г. Калуга, пр-д Киёвский, д. 45б",
            "проспект Маркса" to "Калужская область, г.Обнинск, пр-кт Маркса, д.30б",
            "улица Марии Ульяновой" to "Российская Федерация, Брянская область, городской округ город Брянск, г. Брянск, ул. Марии Ульяновой, земельный участок 12",
            "улица Марии Ульяновой" to "Российская Федерация, Брянская область, городской округ город Брянск, г. Брянск, Марии Ульяновой ул, земельный участок 12"
        )


        testData.forEach { osmStreet, egrnStreet ->
            assertEquals(
                osmStreet,
                ParsedStreet.identify(egrnStreet, streetTypes, testData.keys.map { Pair(it, Pair(it, listOf<OsmPrimitive>())) }.toMap()).name
            )
        }

        assertEquals("улица Пупкина",
            ParsedStreet.identify("Москва сити, улица Васимира Пупкина", streetTypes, mapOf("улица Васимира Пупкина" to Pair("улица Пупкина", listOf<OsmPrimitive>()))).name)

        val parsedNumeredStreet = ParsedStreet.identify("Москва сити, проезд 2й Родниковый, 35", streetTypes, mapOf("2-й Родниковый проезд" to Pair("2-й Родниковый проезд", listOf<OsmPrimitive>())))
        assertEquals("2-й Родниковый проезд", parsedNumeredStreet.name)
        assertFalse(parsedNumeredStreet.flags.contains(ParsingFlags.STREET_NAME_FUZZY_MATCH))

    }
}