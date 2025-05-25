package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.fasterxml.jackson.annotation.JsonFormat

enum class NSPDLayer(@JsonFormat(shape = JsonFormat.Shape.OBJECT) val layerId: Int, val description: String) {
    PARCEL(36048, "Кадастровые участки"),
    BUILDING(36049, "Здания"),
    CONSTRUCTS(36328, "Сооружения"),
    UNFINISHED(36329, "Строящиеся объекты"),
    PLACES_BOUNDARIES(36281, "Административные границы НП"),
    MUNICIPALITY_BOUNDARIES(36278, "Границы муниципальных образований");

    fun isBuilding(): Boolean {
        return this == BUILDING || this == UNFINISHED
    }

    fun hasAddressInfo(): Boolean {
        return this == PARCEL || this== BUILDING || this == CONSTRUCTS || this == UNFINISHED
    }

}
