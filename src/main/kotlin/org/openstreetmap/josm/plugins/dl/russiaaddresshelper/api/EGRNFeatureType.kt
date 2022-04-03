package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.fasterxml.jackson.annotation.JsonFormat

enum class EGRNFeatureType (@JsonFormat(shape = JsonFormat.Shape.OBJECT) val type: Int) {
    PARCEL(1),
    UNKNOWN1(2),
    BUILDING(5),
    UNKNOWN2(7);

    companion object {
        fun fromInt(value: Int) = values().first { it.type == value }
    }
}
