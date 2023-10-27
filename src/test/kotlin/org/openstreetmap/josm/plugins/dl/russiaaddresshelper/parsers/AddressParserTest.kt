package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openstreetmap.josm.JOSMFixture
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.PlaceTypes

internal class AddressParserTest {
    private val placeTypes: PlaceTypes = PlaceTypes.byYml("/references/place_types.yml")


    @BeforeEach
    fun setUpBeforeClass() {
        JOSMFixture("src/test/config/unit-josm.home").init()
    }

    @Test
    fun parseTest() {
        val ds = getDataset()

        val addressParser = AddressParser()
        var egrnAddress =
            """обл. Калужская, р-н Жуковский, МО Сельское поселение деревня Верховье, с/т "Коттедж",, уч 23"""
        var parsedAddress = addressParser.parse(egrnAddress, EastNorth.ZERO, ds)
        assertEquals("Верховье", parsedAddress.parsedPlace.name)
      //  assertTrue(parsedAddress.flags.contains(ParsingFlags.UNSUPPORTED_ADDRESS_TYPE))

        val parsedAddress2 = addressParser.parse("Российская Федерация, Калужская область, Дзержинский район, сельское поселение \" Село Совхоз им. Ленина\", с. Совхоз им. Ленина, улица Верхняя, дом 19, сооружение 1", EastNorth.ZERO, ds)

        egrnAddress ="""Российская Федерация, Красноярский край, Городской округ город Ачинск, г. Ачинск, мкр. 1-й"""
        parsedAddress = addressParser.parse(egrnAddress, EastNorth.ZERO, ds)
        assertTrue(parsedAddress.flags.containsAll(listOf(ParsingFlags.PLACE_HAS_NUMBERED_NAME, ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED)))


    }

    private fun getDataset(): DataSet {
        val node1 = Node(EastNorth.ZERO)
        node1.put( "name","Верховье")
        node1.put( "place","village")

        val node3 = Node(EastNorth.ZERO)
        val node2 = Node(EastNorth.ZERO)
        val way = Way()
        way.addNode(node3)
        way.addNode(node2)
        way.put("name", "Верхняя улица")
        way.put("highway", "residential")
        val node4 = Node(EastNorth.ZERO)
        node4.put("name", "1-й микрорайон")
        node4.put("place", "suburb")

        return DataSet(node1, node2, node3, way, node4)
    }

}