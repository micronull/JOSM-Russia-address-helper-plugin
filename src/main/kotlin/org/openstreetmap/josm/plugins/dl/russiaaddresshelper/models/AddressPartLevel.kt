package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

enum class AddressPartLevel(val description: String) {
    POST_INDEX("Индекс"),
    COUNTRY("Страна"),
    REGION("Край, область"),
    DISTRICT("Район"),
    MUNICIPALITY("Городское/сельское поселение"),
    CITY("Город"),
    PLACE("Населенный пункт"),
    CITY_REGION("Район населенного пункта"),
    TERRITORY("Элемент площадной сети"),
    STREET("Элемент дорожной сети"),
    LOT("Участок"),
    BUILDING("Здание"),
    CONSTRUCTION("Сооружение"),
    LETTER("Литера"),
    BUILDING_PART("Часть здания"),
    ROOM("Помещение"),
    FLAT("Квартира")
}
