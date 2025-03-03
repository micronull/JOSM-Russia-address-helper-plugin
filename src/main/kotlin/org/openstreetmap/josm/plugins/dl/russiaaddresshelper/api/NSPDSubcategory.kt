package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.fasterxml.jackson.annotation.JsonFormat

enum class NSPDSubcategory (@JsonFormat(shape = JsonFormat.Shape.OBJECT) val type: Int) {
    //значения этого энама взяты наугад из ответов, могут вообще ничего не означать
    PARCEL(5),
    BUILDING(1);

    companion object {
        fun fromInt(value: Int) = values().first { it.type == value }
    }
}
