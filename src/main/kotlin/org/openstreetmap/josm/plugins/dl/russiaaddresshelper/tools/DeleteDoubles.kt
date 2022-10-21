package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Buildings
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import javax.swing.JOptionPane

/**
 * Обработчик для удаления дублей.
 * Фильтрует переданный список по имеющимся адресам и оставляет здания с наибольшей площадью.
 */
class DeleteDoubles {
    private val osmAddressMap: MutableMap<String, MutableMap<String,EastNorth>> = mutableMapOf()

    init {
        // Предварительно загружаем список адресов из OSM, чтоб по нему удалить загруженные из ЕГРН дубли.
        loadOsmAddress()
    }

    /**
     * Очистка переданного списка от дублей.
     */
    fun clear(items: MutableList<Buildings.Building>): MutableList<Buildings.Building> {
        //удаляем все здания, с пустыми тэгами адреса
        items.removeAll {
            it.preparedTags["addr:street"] == null || it.preparedTags["addr:housenumber"] == null
        }


        //удаляем все здания, которые совпадают по данным адреса с уже существующими в ОСМ
        //находящиеся на расстоянии ближе заданного в настройках
        items.removeAll {
            val street = it.preparedTags["addr:street"]!!
            val house = it.preparedTags["addr:housenumber"]!!
            val centroid = it.coordinate
            if(osmAddressMap.containsKey(street) && osmAddressMap[street]!!.contains(house) &&
                    (centroid?.distance(osmAddressMap[street]?.get(house) ?: centroid) ?: Double.MIN_VALUE) < TagSettingsReader.CLEAR_DOUBLE_DISTANCE.get()) {
                Logging.info("EGRN PLUGIN remove existing in OSM address $street $house")
                val msg = I18n.tr("Removed existing in OSM address double")
                Notification("$msg $street, $house").setIcon(JOptionPane.WARNING_MESSAGE).show()
                return@removeAll true
            }
            return@removeAll false
        }

        val counter: MutableMap<String, MutableMap<String, MutableList<Buildings.Building>>> = mutableMapOf()
        //оставшиеся прочесываем на дубликаты, выстраивая по приоритету площади
        items.forEach {
            val street = it.preparedTags["addr:street"]!!
            val house = it.preparedTags["addr:housenumber"]!!

            if (!counter.containsKey(street)) {
                counter[street] = mutableMapOf()
            }

            if (!counter[street]!!.containsKey(house)) {
                counter[street]!![house] = mutableListOf()
            }

            counter[street]!![house]!!.add(it)
        }

        val newItems: MutableList<Buildings.Building> = mutableListOf()

        counter.forEach { (_, houses) ->
            houses.forEach { (_, items) ->
                if (items.size > 1) {
                    items.sortByDescending { Geometry.computeArea(it.osmPrimitive) }
                    val street = items.first().preparedTags["addr:street"]
                    val house = items.first().preparedTags["addr:housenumber"]
                    Logging.info("EGRN PLUGIN remove found double address, leaving biggest building $street $house")
                    val msg = I18n.tr("Removed found in EGRN address doubles, leaving biggest area building")
                    Notification("$msg $street, $house").setIcon(JOptionPane.WARNING_MESSAGE).show()
                }

                newItems.add(items.first())
            }
        }

        return newItems
    }

    /**
     * Загружаем список адресов из OSM в отдельный массив osmAddressMap.
     * Пока не решена проблема с мультиполигонами, учитывается только здания типа Way
     */
    private fun loadOsmAddress() {
        val primitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives()
        val buildings = primitives.filter { p -> p.hasKey("building") && p.hasKey("addr:street") && p.hasKey("addr:housenumber") }
            .filter { it is Way }.map { it as Way }

        buildings.forEach {
            val street = it.get("addr:street")
            val house = it.get("addr:housenumber")
            val centroid = Geometry.getCentroid(it.nodes)

            if (!osmAddressMap.containsKey(street)) {
                osmAddressMap[street] = mutableMapOf()
            }

            if (!osmAddressMap[street]!!.contains(house)) {
                osmAddressMap[street]!![house] = centroid
            }
        }
    }
}