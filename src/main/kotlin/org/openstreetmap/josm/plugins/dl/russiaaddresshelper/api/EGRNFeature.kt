package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

data class EGRNFeature(
    val type: Int,
    val attrs: EGRNAttribute?,
    val extent: EGRNExtent?,
    val sort: Long,
    val center: EGRNCoord
)

data class EGRNCoord (val x : Double, val y: Double)

data class EGRNExtent(val xmin: Double, val xmax: Double, val ymin:Double, val ymax: Double)

data class EGRNAttribute(val address: String?, val cn: String, val id: String)
