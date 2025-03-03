package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.fasterxml.jackson.annotation.JsonFormat

enum class NSPDLayer(@JsonFormat(shape = JsonFormat.Shape.OBJECT) val layerId: Int, val description: String) {
    PARCEL(36048, "Кадастровые участки"),
    BUILDING(36049, "Здания"),
    CONSTRUCTS(36328, "Сооружения"),
    UNFINISHED(36329, "Строящиеся объекты");

    fun isBuilding(): Boolean {
        return this == BUILDING || this == UNFINISHED
    }

}
