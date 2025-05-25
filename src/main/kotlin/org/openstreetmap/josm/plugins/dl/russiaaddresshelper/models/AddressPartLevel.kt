package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

enum class AddressPartLevel(val description: String, val priority: Int) {
    POST_INDEX("Индекс", 10),
    COUNTRY("Страна",10),
    REGION("Край, область",20),
    DISTRICT("Район",30),
    MUNICIPALITY("Городское/сельское поселение",40),
    CITY("Город", 50),
    PLACE("Населенный пункт",50),
    CITY_REGION("Район населенного пункта",60),
    TERRITORY("Элемент площадной сети", 70),
    STREET("Элемент дорожной сети", 70),
    LOT("Участок", 80), //владение??
    BUILDING("Здание", 80),
    CONSTRUCTION("Сооружение", 90),
    CORPUS("Корпус", 90),
    LETTER("Литера", 100), //следует различать литеру и буквенный инлекс
    BUILDING_PART("Часть здания",110),
    ROOM("Помещение",110),
    FLAT("Квартира",110)
    //этаж?
}
