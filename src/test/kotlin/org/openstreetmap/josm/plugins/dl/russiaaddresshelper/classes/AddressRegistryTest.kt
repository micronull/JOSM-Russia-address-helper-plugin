package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.classes


import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.openstreetmap.josm.JOSMFixture
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.AddressRegistry

class AddressRegistryTest {

    @BeforeEach
    fun setUpBeforeClass() {
        JOSMFixture("src/test/config/unit-josm.home").init()
    }

    @Test
    fun testAddressRegistry() {
        val registry = AddressRegistry()

        val node = getNode("Сосновая улица", "1")
        assertTrue(registry.add(node))
        assertEquals(1, registry.getSize())
        assertTrue(registry.contains(node))

        val node1 = getNode("Сосновая улица", "2")
        assertTrue(registry.add(node1))
        assertTrue(registry.contains(node1))
        val node2 = getNode("Сосновая улица", "1")
        registry.add(node2)
        assertEquals(2, registry.getDoubles(node2).size)
        assertEquals(2, registry.getDoubles("Сосновая улица",null, housenumber = "1").size)
        assertEquals(3, registry.getSize())

        val node3 = getNode("", "1", "Пухлово")
        assertTrue(registry.add(node3))
        assertTrue(registry.contains(node3))
        assertFalse (registry.hasDoubles(node3))

        registry.remove(node1)
        assertTrue(!registry.contains(node1))
        assertEquals(3, registry.getSize())
        assertFalse(registry.remove(node1))

        registry.remove(setOf(node, node2, node3))
        registry.remove(node2)
        assertEquals(0, registry.getSize())
        assertTrue(registry.isEmpty())

    }

    private fun getNode(street: String, house: String, place: String = ""): Node {
        val node = Node(EastNorth.ZERO)
        node.put("addr:housenumber", house)
        node.put("addr:street", street)
        if (place != "") node.put("addr:place", place)
        return node
    }
}