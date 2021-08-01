package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.text.StringEscapeUtils
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EgrnQuery
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.handlers.Doubles
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.HouseNumberParser
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.StreetParser
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.HttpClient
import org.openstreetmap.josm.tools.I18n
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
        var onNotFoundStreetParser: ((street: String) -> Unit)? = null
        var onComplete: ((changeBuildings: Array<OsmPrimitive>) -> Unit)? = null
    }

    class Building(val osmPrimitive: OsmPrimitive) {
        var httpResponse: HttpClient.Response? = null

        private val coordinate: EastNorth?
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

        val preparedTags: MutableMap<String, String> = mutableMapOf()

        fun request() {
            httpResponse = EgrnQuery(coordinate!!).httpClient.connect()
        }
    }

    private var items: MutableList<Building> = mutableListOf()

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
            val channel = requests(loadListener)

            parseResponses(channel, loadListener).awaitAll()

            sanitize()

            val changeBuildings: MutableList<OsmPrimitive> = mutableListOf()

            if (items.size > 0) {
                val cmds: MutableList<Command> = mutableListOf()

                for (building in items) {
                    building.preparedTags.forEach { (key, value) ->
                        cmds.add(ChangePropertyCommand(building.osmPrimitive, key, value))

                        if (!changeBuildings.contains(building.osmPrimitive)) {
                            changeBuildings.add(building.osmPrimitive)
                        }
                    }
                }

                if (cmds.size > 0) {
                    val c: Command = SequenceCommand(I18n.tr("Added tags from RussiaAddressHelper "), cmds)
                    UndoRedoHandler.getInstance().add(c)
                }
            }

            loadListener?.onComplete?.invoke(changeBuildings.toTypedArray())
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

                    runCatching {
                        building.request()
                    }.onSuccess {
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
                    }.onFailure {
                        Logging.warn(it.message)

                        if (items.size - 1 == index) {
                            loadListener?.onResponseContinue?.invoke()
                            channel.close()
                        }
                    }
                } finally {
                    if (scope.isActive) semaphore.release()
                }
            }
        }

        return channel
    }

    @ObsoleteCoroutinesApi private suspend fun parseResponses(channel: Channel<Building>, loadListener: LoadListener? = null): MutableList<Deferred<Void?>> {
        val defers: MutableList<Deferred<Void?>> = mutableListOf()
        val streetParser = StreetParser()
        val houseNumberParser = HouseNumberParser()

        for (building in channel) {
            defers += scope.async {
                runCatching {
                    building.httpResponse!!.contentReader.readText()
                }.onSuccess {
                    val match = Regex("""address":\s"(.+?)"""").find(StringEscapeUtils.unescapeJson(it))

                    if (match == null) {
                        Logging.error("Parse EGRN response error.")
                    } else {
                        val address = match.groupValues[1]
                        val osmPrimitive = building.osmPrimitive

                        if (TagSettingsReader.EGRN_ADDR_RECORD.get() && !osmPrimitive.hasTag("addr:RU:egrn")) {
                            building.preparedTags["addr:RU:egrn"] = address
                        }

                        val streetParse = streetParser.parse(address)
                        val houseNumberParse = houseNumberParser.parse(address)

                        if (streetParse != "") {
                            if (houseNumberParse != "") {
                                if (!osmPrimitive.hasTag("addr:housenumber")) {
                                    building.preparedTags["addr:housenumber"] = houseNumberParse
                                }

                                if (!osmPrimitive.hasTag("addr:street")) {
                                    building.preparedTags["addr:street"] = streetParse
                                }

                                if ("addr:housenumber" in building.preparedTags || "addr:street" in building.preparedTags) {
                                    building.preparedTags["fixme"] = "Адрес загружен из ЕГРН, требуется проверка правильности заполнения тегов."
                                    building.preparedTags["source:addr"] = "ЕГРН"
                                }
                            }
                        } else {
                            loadListener?.onNotFoundStreetParser?.invoke(streetParser.extracted)
                        }
                    }
                }.onFailure {
                    Logging.error(it.message)
                }

                null
            }
        }

        return defers
    }

    private fun sanitize(){
        items.removeAll { it.preparedTags.isEmpty() }

        items = Doubles().clear(items)
    }

    private fun filter() {
        items.removeAll {
            val el = it.osmPrimitive

            el !is Way || !el.hasKey("building") || el.hasKey("fixme") || el.hasKey("addr:housenumber")
        }
    }
}