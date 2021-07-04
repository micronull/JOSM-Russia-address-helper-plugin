package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

interface Parser {
    val regexList: Collection<Regex>
        get() = listOf(Regex("ул\\S*\\s+(?<street>.+?),\\s*(?:д.*?)?\\s+(?<housenumber>\\d.*)", setOf(RegexOption.IGNORE_CASE, RegexOption.UNIX_LINES)))

    fun parse(egrnAddress: String): String
}