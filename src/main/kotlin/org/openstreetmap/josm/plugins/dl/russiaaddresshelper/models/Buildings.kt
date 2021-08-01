package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPluginAction
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EgrnQuery
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.EgrnSettingsReader
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.HttpClient
import org.openstreetmap.josm.tools.Logging

class Buildings(selected: List<OsmPrimitive>) {
    @ObsoleteCoroutinesApi private val scope = CoroutineScope(newSingleThreadContext("EGRN requests"))

    val size: Int
        get() {
            return items.size
        }

    class LoadListener {
        var onResponse: ((res: HttpClient.Response?) -> Unit)? = null
        var onResponseContinue: (() -> Unit)? = null
    }

    private class Building(val osmPrimitive: OsmPrimitive) {
        var httpResponse: HttpClient.Response? = null
        val coordinate: EastNorth?
            get() {
                return when (osmPrimitive) {
                    is Way -> {
                        Geometry.getCentroid(osmPrimitive.nodes)
                    }
                    else -> {
                        null
                    }
                }
            }

        fun request() {
            httpResponse = EgrnQuery(coordinate!!).httpClient.connect()
        }
    }

    private val items: MutableList<Building> = mutableListOf()

    init {
        selected.forEach {
            items.add(Building(it))
        }
        filter()
    }

    fun isNotEmpty(): Boolean {
        return items.isNotEmpty()
    }

    @ObsoleteCoroutinesApi fun load(loadListener: LoadListener? = null): CoroutineScope {
        scope.launch {
            requests(loadListener)


        }
        return scope
    }

    @ObsoleteCoroutinesApi private fun requests(loadListener: LoadListener? = null): Channel<Building> {
        val limit = EgrnSettingsReader.REQUEST_LIMIT.get()
        val semaphore = kotlinx.coroutines.sync.Semaphore(limit)
        val channel = Channel<Building>()

        items.mapIndexed { index, building ->
            scope.launch {
                try {
                    semaphore.acquire()

                    building.request()

                    if (building.httpResponse?.responseCode == 200) {
                        channel.send(building)
                    }

                    loadListener?.onResponse?.invoke(building.httpResponse)

                    if (items.size - 1 == index) {
                        loadListener?.onResponseContinue?.invoke()
                        channel.close()
                    } else if (items.size - limit >= index) {
                        delay((EgrnSettingsReader.REQUEST_DELAY.get() * 1000).toLong())
                    }
                } finally {
                    if (scope.isActive) semaphore.release()
                }
            }
        }

        return channel
    }

    private fun filter() {
        items.removeAll {
            val el = it.osmPrimitive

            el !is Way || !el.keys.containsKey("building") || el.keys.containsKey("fixme") || el.keys.containsKey("addr:housenumber")
        }
    }
}