package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Buildings
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.ValidationSettingsReader
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
     * Очистка переданного списка от дублей - не удаляем из списка, но очищаем адресную инфу, попутно помечая объекты для валиДатора.
     */
    fun clearAddresses(items: MutableList<Buildings.Building>): MutableList<Buildings.Building> {
        try {
            items.forEach {
                //костыль - просто пропускаем здания без адреса
                if (!it.preparedTags.contains("addr:housenumber")) return@forEach

                val streetOrPlace = if (StringUtils.isNotBlank(it.preparedTags["addr:street"])) {
                    it.preparedTags["addr:street"]
                } else {
                    it.preparedTags["addr:place"]
                }
                val house = it.preparedTags["addr:housenumber"]!!
                val itemCentroid = it.coordinate
                //перепроверить проблемы старого алгоритма в новом
                if (ValidationSettingsReader.ENABLE_NEW_DOUBLES_CHECK.get()) {
                    val primitive = if (it.osmPrimitive is Node && it.importedGeometry.isNotEmpty()) { it.importedGeometry.first().second!!} else {it.osmPrimitive}
                    if (RussiaAddressHelperPlugin.addressRegistry.getDoubles(primitive, it.preparedTags).isNotEmpty()) {
                        processDouble(it)
                    }
                } else {
                    //если мы включаем режим "валидации", то есть запрашиваем данные ЕГРН для которых уже есть адресные данные в ОСМ,
                    //то тут происходит "интересное" - примитив здания сравнивается сам с собой, и считается дубликатом, поэтому вторичные тэги ему не присваиваются
                    //eще один фатальный баг - мапа адресов ОСМ хранит только одно сочетание "улица - номер дома - координата".
                    //поэтому, если присутствует два одинаковых адреса, а в мапу попал дальний от текущего запроса - то будет выдан адрес-дубль
                    if (osmAddressMap.containsKey(streetOrPlace) && osmAddressMap[streetOrPlace]!!.contains(house)) {
                        val distance = itemCentroid.distance(osmAddressMap[streetOrPlace]?.get(house) ?: itemCentroid)
                        if (distance < CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.get()) {
                            Logging.info("EGRN PLUGIN remove existing in OSM address $streetOrPlace $house")
                            processDouble(it)
                        } else {
                            Logging.info(
                                "EGRN PLUGIN found double for address, but not mark it for removal, because distance $distance is bigger than ${CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.get()}"
                            )
                        }
                    }
                }
            }


            //если запрос идёт по большой площади, может оказаться что новые адреса находятся в разных местах! но будут считаться дублями
            val counter: MutableMap<String, MutableMap<String, MutableList<Buildings.Building>>> = mutableMapOf()
            //оставшиеся прочесываем на дубликаты, выстраивая по приоритету площади
            items.forEach {
                val streetOrPlace = if (StringUtils.isNotBlank(it.preparedTags["addr:street"])) {
                    it.preparedTags["addr:street"]
                } else {
                    it.preparedTags["addr:place"]
                }
                if (streetOrPlace == null || (it.osmPrimitive is Node && it.importedGeometry.isEmpty())) return@forEach
                val house = it.preparedTags["addr:housenumber"] ?: return@forEach

                if (!counter.containsKey(streetOrPlace)) {
                    counter[streetOrPlace] = mutableMapOf()
                }

                if (!counter[streetOrPlace]!!.containsKey(house)) {
                    counter[streetOrPlace]!![house] = mutableListOf()
                }

                counter[streetOrPlace]!![house]!!.add(it)
            }

            counter.forEach { (_, houses) ->
                houses.forEach { (_, doubles) ->
                    if (doubles.size > 1) {
                        doubles.sortByDescending {
                            if (it.osmPrimitive is Node) {
                                Geometry.computeArea(it.importedGeometry.first().second)
                            } else {
                                Geometry.computeArea(it.osmPrimitive)
                            }
                        }
                        val street = if (doubles.first().preparedTags["addr:street"] != null) {
                            doubles.first().preparedTags["addr:street"]
                        } else {
                            doubles.first().preparedTags["addr:place"]
                        }
                        val house = doubles.first().preparedTags["addr:housenumber"]

                        Logging.info("EGRN PLUGIN remove found double address, leaving biggest building $street $house")
                        val msg = I18n.tr("Removed found in EGRN address doubles, leaving biggest area building")
                        Notification("$msg $street, $house").setIcon(JOptionPane.WARNING_MESSAGE).show()

                        doubles.toList().drop(1).forEach {
                           processDouble(it)
                        }
                    }
                }
            }

            return items
        } catch (ex: Exception) {
            Logging.error(ex)
        }
        return mutableListOf()
    }

    private fun processDouble(it: Buildings.Building) {
        var primitiveToAssignTags: OsmPrimitive? = null

        if (it.osmPrimitive is Node) {
            //если это импортированная геометрия, то удаляем адресные тэги
            if (it.importedGeometry.isNotEmpty()) {
                primitiveToAssignTags = it.importedGeometry.first().second!!
            }
        } else { //запрос был по зданию
            primitiveToAssignTags = it.osmPrimitive
        }
        if (primitiveToAssignTags != null) {
            if (!ValidationSettingsReader.ENABLE_NEW_DOUBLES_CHECK.get()) {
                RussiaAddressHelperPlugin.cache.markProcessed(
                    primitiveToAssignTags,
                    EGRNTestCode.EGRN_ADDRESS_DOUBLE_FOUND
                )
            }
            removePreparedAddressTags(it)
        }
    }

    private fun removePreparedAddressTags(it: Buildings.Building) {
        setOf(
            "addr:street",
            "addr:place",
            "addr:housenumber",
            "source:addr"
        ).forEach { addrKey -> it.preparedTags.remove(addrKey) }
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