package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Buildings
import org.openstreetmap.josm.tools.Geometry

/**
 * Обработчик для удаления дублей.
 * Фильтрует переданный список по имеющимся адресам и оставляет здания с наибольшей площадью.
 */
class DeleteDoubles {
    private val osmAddressMap: MutableMap<String, MutableList<String>> = mutableMapOf()

    init {
        loadOsmAddress()
    }

    /**
     * Очистка переданного списка от дублей.
     */
    fun clear(items: MutableList<Buildings.Building>): MutableList<Buildings.Building> {
        items.removeAll {
            val street = it.preparedTags["addr:street"]!!
            val house = it.preparedTags["addr:housenumber"]!!
            osmAddressMap.containsKey(street) && osmAddressMap[street]!!.contains(house)
        }

        val counter: MutableMap<String, MutableMap<String, MutableList<Buildings.Building>>> = mutableMapOf()

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
                }

                newItems.add(items.first())
            }
        }

        return newItems
    }

    /**
     * Загружаем список адресов из OSM в osmAddressMap.
     */
    private fun loadOsmAddress() {
        val primitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives()
        val buildings = primitives.filter { p -> p.hasKey("building") && p.hasKey("addr:street") && p.hasKey("addr:housenumber") }

        buildings.forEach {
            val street = it.get("addr:street")
            val house = it.get("addr:housenumber")

            if (!osmAddressMap.containsKey(street)) {
                osmAddressMap[street] = mutableListOf()
            }

            if (!osmAddressMap[street]!!.contains(house)) {
                osmAddressMap[street]!!.add(house)
            }
        }
    }
}