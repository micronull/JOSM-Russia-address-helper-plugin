package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.core.isSuccessful
import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.github.kittinunf.result.Result
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.openstreetmap.josm.command.AddCommand
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EGRNFeatureType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EGRNResponse
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsedAddressInfo
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.DeleteDoubles
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.swing.JOptionPane

// FIXME: такой молодой, а уже легаси...
class Buildings(objects: List<OsmPrimitive>) {
    private val scope: CoroutineScope = CoroutineScope(CoroutineName("EGRN requests"))

    val size: Int
        get() {
            return items.size
        }

    class LoadListener {
        var onResponse: ((res: Response) -> Unit)? = null
        var onResponseContinue: (() -> Unit)? = null
        var onNotFoundStreetParser: ((List<Pair<String, String>>) -> Unit)? = null
        var onComplete: ((changeBuildings: Array<OsmPrimitive>) -> Unit)? = null
    }

    private val CONSECUTIVE_FAILURE_LIMIT = 10

    class Building(val osmPrimitive: OsmPrimitive) {
        val coordinate: EastNorth?
            get() {
                return when (osmPrimitive) {
                    is Way -> {
                        //редкая, но реальная проблема - для сложных зданий центроид находится вне здания и вне участка
                        //пример - addr:RU:egrn=Красноярский край, г. Минусинск, ул. Гоголя, 28 (53.7135362, 91.6851041)
                        //реализован алгоритм - полигон бьется на треугольники, находим их центроид,
                        // если он внутри полигона здания, возвращаем его
                        val centroid = Geometry.getCentroid(osmPrimitive.nodes)
                        return if (Geometry.nodeInsidePolygon(Node(centroid), osmPrimitive.nodes)) {
                            centroid
                        } else {
                            val nodes = osmPrimitive.nodes
                            for (i in 0 until nodes.size - 1) {
                                val node1 = nodes[i]
                                var j = i + 1
                                if (j > nodes.size - 1) j = j - nodes.size
                                val node2 = nodes[j]
                                var k = i + 2
                                if (j > nodes.size - 1) k = k - nodes.size
                                val node3 = nodes[k]
                                val triangleCentroid = Geometry.getCentroid(listOf(node1, node2, node3))
                                if (Geometry.nodeInsidePolygon(
                                        Node(triangleCentroid),
                                        osmPrimitive.nodes
                                    )
                                ) return triangleCentroid
                            }
                            Geometry.getClosestPrimitive(Node(centroid), osmPrimitive.nodes).eastNorth
                        }
                    }
                    else -> {
                        null
                    }
                }
            }

        val preparedTags: MutableMap<String, String> = mutableMapOf()

        val addressNodes: MutableList<Node> = mutableListOf()

        fun request(): Request {
            /*    val someJson = ""
                val res =  Response().apply {
                    data = someJson.toByteArray()
                    statusCode = 200
                    httpResponseMessage = "OK" }*/
            return RussiaAddressHelperPlugin.getEgrnClient()
                .request(coordinate!!, listOf(EGRNFeatureType.PARCEL, EGRNFeatureType.BUILDING))
        }
    }

    private var items: MutableList<Building> = mutableListOf()

    init {
        objects.forEach {
            items.add(Building(it))
        }
    }

    fun isNotEmpty(): Boolean {
        return items.isNotEmpty()
    }

    fun load(loadListener: LoadListener? = null): CoroutineScope {
        scope.launch {
            val channel = requests(loadListener)

            parseResponses(channel, loadListener).awaitAll()

            val map = MainApplication.getMap()
            val ds = map.mapView.layerManager.editDataSet
            val cmds: MutableList<Command> = mutableListOf()
            for (building in items) {
                building.addressNodes.forEach { node ->
                    cmds.add(AddCommand(ds, node))
                }
            }

            sanitize()

            val changeBuildings: MutableList<OsmPrimitive> = mutableListOf()

            if (items.size > 0) {
                for (building in items) {
                    building.preparedTags.forEach { (key, value) ->
                        cmds.add(ChangePropertyCommand(building.osmPrimitive, key, value))
                        if (!changeBuildings.contains(building.osmPrimitive)) {
                            changeBuildings.add(building.osmPrimitive)
                        }
                    }
                }
            }

            if (cmds.size > 0) {
                val c: Command = SequenceCommand(I18n.tr("Added tags from RussiaAddressHelper "), cmds)
                UndoRedoHandler.getInstance().add(c)

            }

            loadListener?.onComplete?.invoke(changeBuildings.toTypedArray())
        }
        return scope
    }

    data class ChannelData(val building: Building, val responseBody: EGRNResponse)

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun requests(loadListener: LoadListener? = null): Channel<ChannelData> {
        val limit = EgrnSettingsReader.REQUEST_LIMIT.get()
        val semaphore = kotlinx.coroutines.sync.Semaphore(limit)
        val channel = Channel<ChannelData>()
        val startTime = LocalDateTime.now()
        var requestsTotal = 0L
        var retriesTotal = 0L
        var failuresTotal = 0L
        var noRetriesLeft = 0L
        var processedItems = 0
        //val totalTime =
        var consecutiveFailures = 0
        items.mapIndexed { index, building ->
            scope.launch {
                try {
                    semaphore.acquire()
                    var retries = 5
                    var needToRepeat = true
                    while (needToRepeat) {
                        try {
                            val (_, response, result) = building.request()
                                .responseObject<EGRNResponse>(jacksonDeserializerOf())
                            requestsTotal++
                            RussiaAddressHelperPlugin.totalRequestsPerSession++
                            needToRepeat = false
                            when (result) {
                                is Result.Success -> {
                                    RussiaAddressHelperPlugin.totalSuccessRequestsPerSession++
                                    if (!channel.isClosedForSend) {
                                        if (response.isSuccessful) {
                                            channel.send(ChannelData(building, result.value))
                                        }

                                        loadListener?.onResponse?.invoke(response)
                                        processedItems++
                                        consecutiveFailures = 0
                                        if (processedItems == items.size) {
                                            val finishTime = LocalDateTime.now()
                                            printReport(
                                                requestsTotal,
                                                failuresTotal,
                                                retriesTotal,
                                                noRetriesLeft,
                                                startTime,
                                                finishTime
                                            )
                                            loadListener?.onResponseContinue?.invoke()
                                            channel.close()
                                        } else /*if (items.size - limit >= index)*/ {
                                            delay((EgrnSettingsReader.REQUEST_DELAY.get() * 100).toLong())
                                        }
                                    }
                                }
                                is Result.Failure -> {
                                    failuresTotal++
                                    consecutiveFailures++
                                    if (retries > 0) {
                                        needToRepeat = true
                                        retriesTotal++
                                    } else {
                                        loadListener?.onResponse?.invoke(response)
                                        noRetriesLeft++
                                        processedItems++
                                        //add as failed request
                                    }
                                    Logging.info("EGRN-PLUGIN Request failure, retries $retries")
                                    Logging.warn(result.getException().message)
                                    retries--
                                    val isBanned = consecutiveFailures > CONSECUTIVE_FAILURE_LIMIT
                                    if (processedItems == items.size || isBanned) {
                                        val finishTime = LocalDateTime.now()
                                        printReport(
                                            requestsTotal,
                                            failuresTotal,
                                            retriesTotal,
                                            noRetriesLeft,
                                            startTime,
                                            finishTime
                                        )
                                        if (isBanned) {
                                            val msg =
                                                I18n.tr("Too many consecutive failures, your IP maybe banned from EGRN side (")
                                            Notification(msg).setIcon(JOptionPane.WARNING_MESSAGE).show()
                                        }
                                        loadListener?.onResponseContinue?.invoke()
                                        channel.close()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Logging.warn("EGRN UNPROCESSED EXCEPTION: ${e.message}")
                        }
                    }
                } finally {
                    if (scope.isActive) semaphore.release()
                }
            }
        }


        return channel
    }

    private fun printReport(
        requestsTotal: Long,
        failuresTotal: Long,
        retriesTotal: Long,
        noRetriesLeft: Long,
        startTime: LocalDateTime?,
        finishTime: LocalDateTime?
    ) {
        Logging.info("EGRN-PLUGIN report:")
        Logging.info("EGRN-PLUGIN total requests per session: ${RussiaAddressHelperPlugin.totalRequestsPerSession}")
        Logging.info("EGRN-PLUGIN total SUCCESS requests per session: ${RussiaAddressHelperPlugin.totalSuccessRequestsPerSession}")
        Logging.info("EGRN-PLUGIN total requests: $requestsTotal")
        Logging.info("EGRN-PLUGIN total failures: $failuresTotal")
        Logging.info("EGRN-PLUGIN total retries: $retriesTotal, average ${retriesTotal / requestsTotal.toFloat()}")
        Logging.info("EGRN-PLUGIN no retries left failures: $noRetriesLeft")
        Logging.info(
            "EGRN-PLUGIN time elapsed: ${
                ChronoUnit.MINUTES.between(
                    startTime,
                    finishTime
                )
            } min ${ChronoUnit.SECONDS.between(startTime, finishTime)} sec"
        )
    }

    private suspend fun parseResponses(
        channel: Channel<ChannelData>,
        loadListener: LoadListener? = null
    ): MutableList<Deferred<Void?>> {
        val defers: MutableList<Deferred<Void?>> = mutableListOf()

        for (d in channel) {
            defers += scope.async {
                val egrnResponse = d.responseBody

                Logging.info("EGRN-PLUGIN Got data from EGRN: $egrnResponse")
                if (egrnResponse.total == 0) {
                    Logging.info("EGRN PLUGIN empty response")
                    Logging.info("$egrnResponse")
                    RussiaAddressHelperPlugin.egrnResponses[d.building.osmPrimitive] =
                        Triple(d.building.coordinate, egrnResponse, ParsedAddressInfo(listOf()))
                } else if (egrnResponse.results.all { it.attrs?.address?.isBlank() != false }) {
                    Logging.info("EGRN PLUGIN no addresses found for for request $egrnResponse")
                    RussiaAddressHelperPlugin.egrnResponses[d.building.osmPrimitive] =
                        Triple(d.building.coordinate, egrnResponse, ParsedAddressInfo(listOf()))
                } else {
                    //изменения которые надо внести:
                    //реализовать все требующие проверки и исправления ситуации через валидаторы
                    //- нет ответа - connection-refused после n попыток
                    //искать дубликаты адреса в ОСМ здесь. если найдены - выкидывать в валидатор, не присваивать
                    //убрать старый функционал
                    //новый функционал: проверка адресов для уже имеющих адреса зданий. Получать адреса парсить и
                    // сравнивать, при несовпадении - выкидывать в валидатор

                    RussiaAddressHelperPlugin.removeFromAllCaches(d.building.osmPrimitive)
                    val parsedAddressInfo = egrnResponse.parseAddresses(d.building.coordinate!!)

                    RussiaAddressHelperPlugin.egrnResponses[d.building.osmPrimitive] =
                        Triple(d.building.coordinate, egrnResponse, parsedAddressInfo)

                    val preferredOsmAddress = parsedAddressInfo.getPreferredAddress()
                    if (preferredOsmAddress != null) {
                        val osmPrimitive = d.building.osmPrimitive
                        //костыль чтобы не присваивать адрес если есть проблемы

                        if (canAssignAddress(parsedAddressInfo)) {
                            if (TagSettingsReader.EGRN_ADDR_RECORD.get()) {
                                d.building.preparedTags["addr:RU:egrn"] = preferredOsmAddress.egrnAddress
                            }

                            //спорное решение - добавляем зданию адрес БЕЗ номеров квартир
                            d.building.preparedTags.plusAssign(preferredOsmAddress.getOsmAddress().getBaseAddressTags())
                            if (!osmPrimitive.hasTag("addr:housenumber")) {
                                d.building.preparedTags["source:addr"] = "ЕГРН"
                            }
                        }

                    }
                }
                null
            }
        }
        return defers
    }

    private fun sanitize() {
        items.removeAll { it.preparedTags.isEmpty() }

        if (items.isNotEmpty()) {
            items = DeleteDoubles().clear(items)
        }
    }

    //TO DO: все это по ходу члены ParsedAddressInfo
    private fun canAssignAddress(addressInfo: ParsedAddressInfo): Boolean {
        val validAddresses = addressInfo.getValidAddresses()
        if (validAddresses.size != 1) return false //multiple valid addresses or no valid address
        if (addressInfo.addresses.size != 1) { //has 1 valid address and more non-valid
            val nonValidAddresses = addressInfo.getNonValidAddresses()
            if (nonValidAddresses.any { nonValidAddressCanBeFixed(it) }) return false //do we have any non-valid but potentially fixable?
        }
        val preferredAddress = addressInfo.getPreferredAddress()
        if (preferredAddress != null) {
            return checkValidAddress(preferredAddress)
        }
        return false
    }

    private fun checkValidAddress(address: ParsedAddress): Boolean {
        return !address.flags.contains(ParsingFlags.STREET_NAME_FUZZY_MATCH) &&
                !address.flags.contains(ParsingFlags.STREET_NAME_INITIALS_MATCH) &&
                !address.flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM) &&
                !((address.flags.contains(ParsingFlags.PLACE_NAME_INITIALS_MATCH) || address.flags.contains(
                    ParsingFlags.PLACE_NAME_FUZZY_MATCH
                ))
                        && !address.getOsmAddress().isFilledStreetAddress())
    }

    private fun nonValidAddressCanBeFixed(address: ParsedAddress): Boolean {
        return (address.flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM) ||
                address.flags.contains(ParsingFlags.CANNOT_FIND_PLACE_OBJECT_IN_OSM))
                && isHouseNumberValid(address)
    }

    private fun isHouseNumberValid(address: ParsedAddress): Boolean {
        return !address.flags.contains(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED) &&
                !address.flags.contains(ParsingFlags.HOUSENUMBER_TOO_BIG)
                && !address.flags.contains(ParsingFlags.HOUSENUMBER_CANNOT_BE_PARSED_BUT_CONTAINS_NUMBERS)
    }
}