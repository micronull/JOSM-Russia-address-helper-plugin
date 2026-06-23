package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Buildings
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.EGRNTestCode
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import javax.swing.JOptionPane

/**
 * Обработчик для удаления дублей.
 * Фильтрует переданный список по имеющимся адресам и оставляет здания с наибольшей площадью.
 * Сильно требует рефакторинга
 */
class DeleteDoubles {
    private val osmAddressMap: MutableMap<String, MutableMap<String, EastNorth>> = mutableMapOf()

    init {
        // Предварительно загружаем список адресов из OSM, чтоб по нему удалить загруженные из ЕГРН дубли.
        loadOsmAddress()
    }

    /**
     * Очистка переданного списка от дублей.
     */
    fun clear(items: MutableList<Buildings.Building>): MutableList<Buildings.Building> {
        try {
        //удаляем все здания, с пустыми тэгами адреса (пропуская импортированные по линии)
        items.removeAll {
            it.osmPrimitive !is Node && ((it.preparedTags["addr:street"] == null).and(it.preparedTags["addr:place"] == null) || it.preparedTags["addr:housenumber"] == null)
        }

        //удаляем все здания, которые совпадают по данным адреса с уже существующими в ОСМ
        //находящиеся на расстоянии ближе заданного в настройках
        items.removeAll {
            //костыль - просто пропускаем здания с импортированной геометрией без адреса
            if (it.osmPrimitive is Node && !it.preparedTags.contains("addr:housenumber")) return@removeAll false

            val streetOrPlace = if (StringUtils.isNotBlank(it.preparedTags["addr:street"])) {
                it.preparedTags["addr:street"]
            } else {
                it.preparedTags["addr:place"]
            }
            val house = it.preparedTags["addr:housenumber"]!!
            val itemCentroid = it.coordinate
            //если мы включаем режим "валидации", то есть запрашиваем данные ЕГРН для которых уже есть адресные данные в ОСМ,
            //то тут происходит "интересное" - примитив здания сравнивается сам с собой, и считается дубликатом, поэтому вторичные тэги ему не присваиваются

            //eще один фатальный баг - мапа адресов ОСМ хранит только одно сочетание "улица - номер дома - координата".
            //поэтому, если присутствует два одинаковых адреса, а в мапу попал дальний от текущего запроса - то будет выдан адрес-дубль
            if (osmAddressMap.containsKey(streetOrPlace) && osmAddressMap[streetOrPlace]!!.contains(house)) {
                val distance = itemCentroid.distance(osmAddressMap[streetOrPlace]?.get(house) ?: itemCentroid)
                if (distance < CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.get()) {
                    Logging.info("EGRN PLUGIN remove existing in OSM address $streetOrPlace $house")
                    //TODO debug code until deduplication through validator will be implemented
                    //принять решение, сейчас дубликаты чистятся на этапе обработки, затем обработанные кидаются в валидатор по этому признаку
                    if (it.osmPrimitive is Node && it.importedGeometry.isNotEmpty()) {
                        //если это импортированная геометрия, то удаляем адресные тэги
                        RussiaAddressHelperPlugin.cache.markProcessed(
                            it.importedGeometry.first().second!!,
                            EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND
                        )
                        setOf("addr:street", "addr:place", "addr:housenumber", "source:addr").forEach{addrKey -> it.preparedTags.remove(addrKey)}
                        return@removeAll false
                    } else {
                        RussiaAddressHelperPlugin.cache.markProcessed(
                            it.osmPrimitive,
                            EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND
                        )
                        return@removeAll true
                    }
                } else {
                    Logging.info(
                        "EGRN PLUGIN found double for address, but not mark it for removal, because distance $distance is bigger than ${CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.get()}"
                    )
                }
            }
            return@removeAll false
        }

        val newItems: MutableSet<Buildings.Building> = mutableSetOf()
        val counter: MutableMap<String, MutableMap<String, MutableList<Buildings.Building>>> = mutableMapOf()
        //оставшиеся прочесываем на дубликаты, выстраивая по приоритету площади
        items.forEach {
            val streetOrPlace = if (StringUtils.isNotBlank(it.preparedTags["addr:street"])) {
                it.preparedTags["addr:street"]
            } else {
                it.preparedTags["addr:place"]
            }
            val house = it.preparedTags["addr:housenumber"]
            if (streetOrPlace == null || house == null || (it.osmPrimitive is Node && it.importedGeometry.isEmpty())) {
                if (it.osmPrimitive is Node) {
                    newItems.add(it)
                }
                return@forEach
            }

            if (!counter.containsKey(streetOrPlace)) {
                counter[streetOrPlace] = mutableMapOf()
            }

            if (!counter[streetOrPlace]!!.containsKey(house)) {
                counter[streetOrPlace]!![house] = mutableListOf()
            }

            counter[streetOrPlace]!![house]!!.add(it)
        }

        counter.forEach { (_, houses) ->
            houses.forEach { (_, items) ->
                if (items.size > 1) {
                    items.sortByDescending { if (it.osmPrimitive is Node) {Geometry.computeArea(it.importedGeometry.first().second)} else {Geometry.computeArea(it.osmPrimitive)}}
                    val street = if (items.first().preparedTags["addr:street"] != null) {
                        items.first().preparedTags["addr:street"]
                    } else {
                        items.first().preparedTags["addr:place"]
                    }
                    val house = items.first().preparedTags["addr:housenumber"]

                    Logging.info("EGRN PLUGIN remove found double address, leaving biggest building $street $house")
                    val msg = I18n.tr("Removed found in EGRN address doubles, leaving biggest area building")
                    Notification("$msg $street, $house").setIcon(JOptionPane.WARNING_MESSAGE).show()
                    //debug code until deduplication through validator will be implemented
                    val otherPrimitives = items.toList().drop(1).map {
                        if (it.osmPrimitive is Node) {
                            setOf("addr:street", "addr:place", "addr:housenumber", "source:addr").forEach{addrKey -> it.preparedTags.remove(addrKey)}
                            newItems.add(it)
                            it.importedGeometry.first().second!!
                        } else {
                            it.osmPrimitive}
                    }.toSet()
                    RussiaAddressHelperPlugin.cache.markProcessed(
                        otherPrimitives,
                        EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND
                    )
                }
                newItems.add(items.first())
            }
        }

        return newItems.toMutableList()
        } catch (ex :Exception) {
            Logging.error(ex)
        }
        return mutableListOf()
    }

    /**
     * Загружаем список адресов из OSM в отдельный массив osmAddressMap.
     * Должно работать так же и с мультиполигонами
     */
    private fun loadOsmAddress() {
        val primitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives()
        val buildings = primitives.filter { p ->
            p !is Node &&
                    p.hasTag("building")
                    && p.hasTag("addr:housenumber")
                    && (p.hasTag("addr:street") || p.hasTag("addr:place"))
        }
            .map { it }

        buildings.forEach {
            val streetOrPlace = if (StringUtils.isNotBlank(it.get("addr:street"))) {
                it.get("addr:street")
            } else {
                it.get("addr:place")
            }

            val house = it.get("addr:housenumber")
            val centroid = GeometryHelper.getPrimitiveCentroid(it)

            if (!osmAddressMap.containsKey(streetOrPlace)) {
                osmAddressMap[streetOrPlace] = mutableMapOf()
            }

            if (!osmAddressMap[streetOrPlace]!!.contains(house)) {
                osmAddressMap[streetOrPlace]!![house] = centroid
            }
        }
    }
}