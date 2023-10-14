package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openstreetmap.josm.JOSMFixture
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.PlaceTypes

internal class AddressParserTest {
    private val placeTypes: PlaceTypes = PlaceTypes.byYml("/references/place_types.yml")


    @BeforeEach
    fun setUpBeforeClass() {
        JOSMFixture("src/test/config/unit-josm.home").init()
    }

    @Test
    fun parseTest() {

    }

}