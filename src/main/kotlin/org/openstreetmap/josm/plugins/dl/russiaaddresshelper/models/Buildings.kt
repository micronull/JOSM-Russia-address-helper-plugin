package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
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
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.*
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.LayerFilterSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.MassActionSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.DeleteDoubles
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper
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
        val coordinate: EastNorth
            get() {
                return GeometryHelper.getPointIntoPolygon(osmPrimitive)
            }

        val preparedTags: MutableMap<String, String> = mutableMapOf()

        val addressNodes: MutableList<Node> = mutableListOf()

        fun requestNewApi(layer: NSPDLayer): Request {
            return RussiaAddressHelperPlugin.getNSPDClient()
                .request(coordinate, layer, osmPrimitive.bBox)
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

            try {
                val channel = requests(loadListener)
                try {
                    parseResponses(channel, loadListener).awaitAll()
                } catch (ex: Exception) {
                    Logging.error(ex)
                }
            } catch (ex: Exception) {
                Logging.error(ex)
            }

            val map = MainApplication.getMap()
            val ds = map.mapView.layerManager.editDataSet
            val cmds: MutableList<Command> = mutableListOf()
            for (building in items) {
                building.addressNodes.forEach { node ->
                    cmds.add(AddCommand(ds, node))
                }
            }

            sanitize()

            val changedBuildings: MutableList<OsmPrimitive> = mutableListOf()

            if (items.size > 0) {
                for (building in items) {
                    building.preparedTags.forEach { (key, value) ->
                        if (!building.osmPrimitive.hasTag(key)) {
                            cmds.add(ChangePropertyCommand(building.osmPrimitive, key, value))
                        } else {
                            if (key == "building" && building.osmPrimitive.hasTag("building", "yes")) {
                                cmds.add(ChangePropertyCommand(building.osmPrimitive, key, value))
                            }
                        }
                        if (!changedBuildings.contains(building.osmPrimitive)) {
                            changedBuildings.add(building.osmPrimitive)
                        }
                    }
                }
            }

            if (cmds.size > 0) {
                val c: Command = SequenceCommand(I18n.tr("Added tags from RussiaAddressHelper "), cmds)
                UndoRedoHandler.getInstance().add(c)

            }

            loadListener?.onComplete?.invoke(changedBuildings.toTypedArray())
        }
        return scope
    }

    data class ChannelData(val building: Building, val responseBody: NSPDResponse)

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
        var consecutiveFailures = 0
        items.mapIndexed { index, building ->
            scope.launch {
                try {
                    semaphore.acquire()
                    val layersToRequest = LayerFilterSettingsReader.getMassRequestActionEnabledLayers()
                    val nspdResponse = NSPDResponse(mutableMapOf())
                    layersToRequest.forEach { layer ->
                        var retries = 5
                        var needToRepeat = true
                        while (needToRepeat) {
                            try {
                                val (_, response, result) = building.requestNewApi(layer)
                                    .responseObject<GetFeatureInfoResponse>(jacksonDeserializerOf())
                                requestsTotal++
                                RussiaAddressHelperPlugin.totalRequestsPerSession++
                                needToRepeat = false
                                when (result) {
                                    is Result.Success -> {
                                        RussiaAddressHelperPlugin.totalSuccessRequestsPerSession++
                                        nspdResponse.addResponse(result.value, layer)
                                        loadListener?.onResponse?.invoke(response)
                                        consecutiveFailures = 0
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
                                Logging.error(e)
                                throw e
                            }
                        }
                    }
                    if (!channel.isClosedForSend) {
                        if (nspdResponse.isNotEmpty()) {
                            channel.send(ChannelData(building, nspdResponse))
                        }
                        processedItems++
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
                        } else {
                            delay((EgrnSettingsReader.REQUEST_DELAY.get() * 100).toLong())
                        }
                    }
                    //end ForEach
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
            //TODO если внутри скоупа происходит исключение, процесс загрузки просто молча виснет
            //обернуть эксепшоны или избавиться от асинхронности вовсе
            defers += scope.async {
                val egrnResponse = d.responseBody

                Logging.info("EGRN-PLUGIN Got data from EGRN: $egrnResponse")
                if (egrnResponse.isEmpty()) {
                    Logging.info("EGRN PLUGIN empty response")
                    Logging.info("$egrnResponse")
                    RussiaAddressHelperPlugin.cache.add(
                        d.building.osmPrimitive,
                        d.building.coordinate,
                        egrnResponse,
                        ParsedAddressInfo(listOf())
                    )
                } else if (!egrnResponse.hasReadableAddress()) {
                    Logging.info("EGRN PLUGIN no addresses found for for request $egrnResponse")
                    RussiaAddressHelperPlugin.cache.add(
                        d.building.osmPrimitive,
                        d.building.coordinate,
                        egrnResponse,
                        ParsedAddressInfo(listOf())
                    )
                } else {
                    RussiaAddressHelperPlugin.cache.remove(d.building.osmPrimitive)
                    val parsedAddressInfo = egrnResponse.parseAddresses(d.building.coordinate)

                    if (MassActionSettingsReader.EGRN_MASS_ACTION_USE_EXT_ATTRIBUTES.get()) {
                        if (egrnResponse.responses[NSPDLayer.BUILDING] != null) {
                            val egrnBuildingTags =
                                TagHelper.getBuildingTags(egrnResponse.responses[NSPDLayer.BUILDING]?.features?.firstOrNull())
                            d.building.preparedTags.plusAssign(egrnBuildingTags)
                        }
                    }

                    RussiaAddressHelperPlugin.cache.add(
                        d.building.osmPrimitive,
                        d.building.coordinate,
                        egrnResponse,
                        parsedAddressInfo
                    )
                    val preferredOsmAddress = parsedAddressInfo.getPreferredAddress()
                    if (preferredOsmAddress != null) {
                        //костыль чтобы не присваивать адрес если есть проблемы
                        if (parsedAddressInfo.canAssignAddress()) {
                            if (TagSettingsReader.EGRN_ADDR_RECORD.get()) {
                                d.building.preparedTags["addr:RU:egrn"] = preferredOsmAddress.egrnAddress
                            }
                            //спорное решение - добавляем зданию адрес БЕЗ номеров квартир
                            d.building.preparedTags.plusAssign(
                                preferredOsmAddress.getOsmAddress().getBaseAddressTags()
                            )
                            //if (!osmPrimitive.hasTag("addr:housenumber")) {
                            d.building.preparedTags["source:addr"] = "ЕГРН"
                            //  }
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

}