package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.junit.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

internal class TagHelperTest {

    /**
     * Regression test: a delimiter before the current chunk start must not be used,
     * otherwise substring(start, end) is called with end < start.
     */
    @Test
    fun splitLongStringIgnoresDelimiterBeforeChunkStart() {
        val value = "а".repeat(49) + " " + "б".repeat(120)

        val parts = TagHelper.splitLongString(value, 55)

        assertTrue(parts.isNotEmpty())
        assertTrue(parts.all { it.isNotEmpty() })
        assertTrue(parts.all { it.length <= 55 })
        assertEquals(value.filterNot { it.isWhitespace() }, parts.joinToString("").filterNot { it.isWhitespace() })
    }

    @Test
    fun splitLongStringDoesNotExceedMaxChunkSizeAtDelimiter() {
        val value = "а".repeat(55) + "," + "б".repeat(10)

        val parts = TagHelper.splitLongString(value, 55)

        assertTrue(parts.isNotEmpty())
        assertTrue(parts.all { it.length <= 55 })
        assertEquals(value, parts.joinToString(""))
    }

    @Test
    fun splitLongStringHandlesAddressDelimitersAtChunkEdges() {
        val value = "Республика Башкортостан, р-н Калтасинский, с. Краснохолмский, ул. Лесная, д. 24, ".repeat(5)

        val parts = TagHelper.splitLongString(value, 55, true)

        assertTrue(parts.isNotEmpty())
        assertTrue(parts.all { it.isNotEmpty() })
        assertTrue(parts.all { it.length <= 55 })
        assertEquals(value.filterNot { it.isWhitespace() }, parts.joinToString("").filterNot { it.isWhitespace() })
    }
}
