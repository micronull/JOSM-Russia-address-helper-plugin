package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models

import com.github.kittinunf.fuel.core.Request
import com.github.kittinunf.fuel.core.Response
import com.github.kittinunf.fuel.jackson.jacksonDeserializerOf
import com.github.kittinunf.result.Result
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.openstreetmap.josm.command.*
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.*
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.*
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.DeleteDoubles
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper.Companion.generateBuildingMultiPolygon
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper.Companion.collectAllAddressTags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper.Companion.collectAllEgrnTags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper.Companion.getAddressTagsForClickAction
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper.Companion.splitLongValue
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper.Companion.splitLongValues
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
                if (osmPrimitive is Node && importedGeometry.isNotEmpty() && importedGeometry.first().second != null) {
                    return GeometryHelper.getPointIntoPolygon(importedGeometry.first().second!!)
                }
                return GeometryHelper.getPointIntoPolygon(osmPrimitive)
            }

        val preparedTags: MutableMap<String, String> = mutableMapOf()

        //val addressNodes: MutableList<Node> = mutableListOf()

        val importedGeometry: MutableList<Pair<List<Command>, OsmPrimitive?>> = mutableListOf()

        fun requestNewApi(layer: NSPDLayer): Request {
            return RussiaAddressHelperPlugin.getNSPDClient()
                .request(coordinate, layer, osmPrimitive.bBox)
        }
    }

    private var items: MutableList<Building> = mutableListOf()

    private val defaultTagsForBuilding: Map<String, String> = mapOf("source:geometry" to "ЕГРН")
    private val defaultRemoveMeTags: Map<String, String> = mapOf("fixme" to "REMOVE ME!")

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
            /*            for (building in items) {
                            building.addressNodes.forEach { node ->
                                cmds.add(AddCommand(ds, node))
                            }
                        }*/

            sanitize()

            val changedObjects: MutableList<OsmPrimitive> = mutableListOf()

            //разве вся дальнейшая обработка не должна проводиться в хэндлере загрузки, после выхода из скоупа?
            if (items.size > 0) {
                for (building in items) {
                    if (building.osmPrimitive is Node && building.importedGeometry.isNotEmpty()) {
                        cmds.addAll(building.importedGeometry.first().first)
                        val buildingPrimitive = building.importedGeometry.first().second!!
                        if (building.preparedTags.isNotEmpty()) {
                            cmds.add(ChangePropertyCommand(ds, mutableListOf(buildingPrimitive), building.preparedTags))
                        }
                        changedObjects.add(buildingPrimitive)
                    } else {
                        building.preparedTags.forEach { (key, value) ->
                            if (!building.osmPrimitive.hasTag(key)) {
                                cmds.add(ChangePropertyCommand(building.osmPrimitive, key, value))
                            } else {
                                if (TagHelper.overwriteValue(key, building.osmPrimitive[key], value)) {
                                    cmds.add(ChangePropertyCommand(building.osmPrimitive, key, value))
                                }
                            }
                        }
                        if (!changedObjects.contains(building.osmPrimitive)) {
                            changedObjects.add(building.osmPrimitive)
                        }
                    }
                }
            }

            if (cmds.size > 0) {
                val geometryImport = items.isNotEmpty() && items[0].osmPrimitive is Node
                val commandDescription = if (geometryImport) {
                    I18n.tr("Imported geometry and address data by line selection")
                } else {
                    I18n.tr("Import address data for selected objects")
                }
                val c: Command = SequenceCommand(commandDescription, cmds)
                UndoRedoHandler.getInstance().add(c)

                val orthoCmds: MutableList<Command> = mutableListOf()
                items.forEach {
                    if (it.importedGeometry.isNotEmpty()) {
                        val primitive = it.importedGeometry.first().second
                        if (primitive != null) {
                            orthoCmds.addAll(GeometryHelper.orthogonalizePrimitive(primitive))
                        }
                    }
                }
                if (orthoCmds.isNotEmpty()) {
                    UndoRedoHandler.getInstance()
                        .add(SequenceCommand(I18n.tr("Orthogonalize imported geometry"), orthoCmds))
                }
            }

            loadListener?.onComplete?.invoke(changedObjects.toTypedArray())
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
        var isBanned = false
        items.mapIndexed { index, building ->
            scope.launch {
                try {
                    semaphore.acquire()
                    val layersToRequest = LayerFilterSettingsReader.getMassRequestActionEnabledLayers()
                    val nspdResponse = NSPDResponse(mutableMapOf())
                    layersToRequest.forEach { layer ->
                        if (isBanned) {
                            val msg =
                                I18n.tr("Too many consecutive failures, your IP maybe banned from EGRN side 8(")
                            Notification(msg).setIcon(JOptionPane.WARNING_MESSAGE).show()
                            return@forEach
                        }
                        //TODO вынести в настройки
                        var retries = 2
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
                                            val msg =
                                                I18n.tr("Data request error, retries left:") + " $retries"
                                            Notification(msg).setIcon(JOptionPane.WARNING_MESSAGE).show()
                                        } else {
                                            loadListener?.onResponse?.invoke(response)
                                            noRetriesLeft++
                                            processedItems++
                                            //add as failed request
                                        }
                                        Logging.info("EGRN-PLUGIN Request failure, retries $retries")
                                        Logging.warn(result.getException().message)
                                        retries--
                                        isBanned = consecutiveFailures > CONSECUTIVE_FAILURE_LIMIT
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
                val nspdResponse: NSPDResponse = d.responseBody
                Logging.info("EGRN-PLUGIN Got data from EGRN: $nspdResponse")
                if (nspdResponse.isEmpty()) {
                    Logging.info("EGRN PLUGIN empty response")
                    Logging.info("$nspdResponse")
                    RussiaAddressHelperPlugin.cache.add(
                        d.building.osmPrimitive,
                        d.building.coordinate,
                        nspdResponse,
                        ParsedAddressInfo(listOf())
                    )
                } else {
                    var primitive = d.building.osmPrimitive
                    if (primitive is Node) {  //запрос геометрии по линии
                        var geometryLayer: NSPDLayer? = null
                        var buildingGeometryResponse: GetFeatureInfoResponse? = null
                        val layersList =
                            listOf(NSPDLayer.BUILDING, NSPDLayer.UNFINISHED, NSPDLayer.CONSTRUCTS)
                        for (layer in layersList) {
                            if (nspdResponse.responses[layer]?.features?.isNotEmpty() == true) {
                                buildingGeometryResponse = nspdResponse.responses[layer]
                                geometryLayer = layer
                                break
                            }
                        }

                        if (buildingGeometryResponse != null) {
                            val features = buildingGeometryResponse.features
                            if (features.size > 1) {
                                Logging.warn("EGRN PLUGIN more than 1 geometry feature for point building, skipping other ${features.size - 1} ")
                            }
                            val feature = features[0]
                            val tagsForBuilding: MutableMap<String, String> = mutableMapOf()
                            tagsForBuilding.putAll(splitLongValues(feature.getTags("autoremove:egrn:")))
                            //не уверен, что это должно быть тут, внутри
                            val map = MainApplication.getMap()
                            val ds = map.mapView.layerManager.editDataSet

                            if (feature.geometry != null) {
                                tagsForBuilding.putAll(defaultTagsForBuilding)
                                val buildTags: MutableMap<String, String> =
                                    TagHelper.getBuildingTags(feature, geometryLayer!!)
                                if (MassActionSettingsReader.EGRN_MASS_ACTION_USE_EXT_ATTRIBUTES.get()) {
                                    buildTags.putAll(tagsForBuilding)
                                }
                                val generatedBuilding =
                                    generateBuildingMultiPolygon(
                                        feature.geometry,
                                        ds,
                                        buildTags,
                                        mutableMapOf(),
                                        ClickActionSettingsReader.EGRN_CLICK_GEOMETRY_IMPORT_THRESHOLD.get()
                                    )

                                if (generatedBuilding.first.isNotEmpty() && generatedBuilding.second != null) {
                                    d.building.importedGeometry.add(generatedBuilding)
                                    primitive = generatedBuilding.second!!
                                    val parsedAddressInfo = nspdResponse.parseAddresses(d.building.coordinate)

                                    RussiaAddressHelperPlugin.cache.add(
                                        primitive,
                                        d.building.coordinate,
                                        nspdResponse,
                                        parsedAddressInfo
                                    )
                                }
                            }
                        } else {
                            if (MassActionSettingsReader.EGRN_MASS_ACTION_GENERATE_ADDRESS_POINTS.get()) {
                                 val parsedAddressInfo = nspdResponse.parseAddresses(d.building.coordinate)
                                if (parsedAddressInfo.canAssignAddress()) {
                                    val address = parsedAddressInfo.getPreferredAddress()
                                    d.building.preparedTags.plusAssign(getAddressTagsForClickAction(address))
                                } else {
                                    d.building.preparedTags.plusAssign(defaultRemoveMeTags)
                                    d.building.preparedTags.plusAssign(collectAllEgrnTags(nspdResponse))
                                    if (parsedAddressInfo.addresses.isNotEmpty()) {
                                        d.building.preparedTags.plusAssign(collectAllAddressTags(parsedAddressInfo.addresses))
                                    }
                                }
                            }
                        }
                    } else { //обычная обработка здания
                        //можем добавить расширенное инфо даже если нет адреса
                        if (MassActionSettingsReader.EGRN_MASS_ACTION_USE_EXT_ATTRIBUTES.get()) {
                            if (nspdResponse.responses[NSPDLayer.BUILDING] != null) {
                                val egrnBuildingTags =
                                    TagHelper.getBuildingTags(
                                        nspdResponse.responses[NSPDLayer.BUILDING]?.features?.firstOrNull(),
                                        NSPDLayer.BUILDING
                                    )
                                d.building.preparedTags.plusAssign(egrnBuildingTags)
                            } else if (nspdResponse.responses[NSPDLayer.UNFINISHED] != null) {
                                val egrnBuildingTags =
                                    TagHelper.getBuildingTags(
                                        nspdResponse.responses[NSPDLayer.UNFINISHED]?.features?.firstOrNull(),
                                        NSPDLayer.UNFINISHED
                                    )
                                d.building.preparedTags.plusAssign(egrnBuildingTags)
                            }
                        }
                    }
                    if (!nspdResponse.hasReadableAddress()) {
                        Logging.info("EGRN PLUGIN no addresses found for for request $nspdResponse")

                        RussiaAddressHelperPlugin.cache.add(
                            primitive,
                            d.building.coordinate,
                            nspdResponse,
                            ParsedAddressInfo(listOf())
                        )
                    } else {
                        RussiaAddressHelperPlugin.cache.remove(primitive)
                        val parsedAddressInfo = nspdResponse.parseAddresses(d.building.coordinate)

                        RussiaAddressHelperPlugin.cache.add(
                            primitive,
                            d.building.coordinate,
                            nspdResponse,
                            parsedAddressInfo
                        )

                        val preferredOsmAddress = parsedAddressInfo.getPreferredAddress()
                        if (preferredOsmAddress != null) {
                            if (d.building.osmPrimitive is Node) {
                                d.building.preparedTags.putAll(
                                    TagHelper.getAddressTagsForMassAction(
                                        preferredOsmAddress
                                    )
                                )
                            }
                            //костыль чтобы не присваивать адрес если есть проблемы
                            if (parsedAddressInfo.canAssignAddress()) {
                                d.building.preparedTags.plusAssign(splitLongValue("addr:RU:egrn",preferredOsmAddress.egrnAddress))
                                //спорное решение - добавляем зданию адрес БЕЗ номеров квартир
                                d.building.preparedTags.plusAssign(
                                    preferredOsmAddress.getOsmAddress().getBaseAddressTagsWithSource()
                                )
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
        items.removeAll { (it.preparedTags.isEmpty() && it.osmPrimitive !is Node) || (it.osmPrimitive is Node && it.importedGeometry.isEmpty() && it.preparedTags.isEmpty()) }

        //костыль, поскольку алгоритм удаления дублей нужно сильно переработать для случая импорта геометрии
        if (items.isNotEmpty()) {
            items = DeleteDoubles().clear(items)
        }
    }

}