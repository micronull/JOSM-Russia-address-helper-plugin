package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

@kotlinx.serialization.Serializable
data class EGRNFeature(
    val type: Int,
    val attrs: EGRNAttribute?,
    val extent: EGRNExtent?,
    val sort: Long,
    val center: EGRNCoord
)

@kotlinx.serialization.Serializable
data class EGRNCoord (val x : Double, val y: Double)

@kotlinx.serialization.Serializable
data class EGRNExtent(val xmin: Double, val xmax: Double, val ymin:Double, val ymax: Double)

@kotlinx.serialization.Serializable
data class EGRNAttribute(val address: String?, val cn: String, val id: String)
