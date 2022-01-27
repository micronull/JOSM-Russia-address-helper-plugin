package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import kotlinx.serialization.Serializable

/**
 * Тип улицы (улица, переулок и т.п.)
 */
@Serializable data class StreetType(val name: String, val osm: Patterns, val egrn: Patterns)