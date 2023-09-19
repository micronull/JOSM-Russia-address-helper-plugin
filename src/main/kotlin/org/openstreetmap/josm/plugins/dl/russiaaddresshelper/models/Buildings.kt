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
                            retriesTotal++
                            needToRepeat = false
                            when (result) {
                                is Result.Success -> {
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
                                    RussiaAddressHelperPlugin.totalRequestsPerSession++
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
                                            val msg = I18n.tr("Too many consecutive failures, your IP maybe banned from EGRN side (")
                                            Notification(msg).setIcon(JOptionPane.WARNING_MESSAGE).show()
                                        }
                                        loadListener?.onResponseContinue?.invoke()
                                        channel.close()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Logging.warn(e.message)
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
                    //проверять на близость линий и точек, и на вхождение в полигон/мультиполигон здания. (это в парсерах улиц и мест)
                    //если линия/точка слишком далеко (задать через настройки) то не присваиваем, ставим флаг не найденной рядом улицы
                    //аналогично для мультика и полигона
                    //искать дубликаты адреса в ОСМ здесь. если найдены - выкидывать в валидатор, не присваивать
                    //искать дубликаты адреса в распознанном, если найдены - выбирать здание с наибольшей площадью
                    // и переносить адрес на него
                    //убрать старый функционал
                    //новый функционал: проверка адресов для уже имеющих адреса зданий. Получать адреса парсить и
                    // сравнивать, при несовпадении - выкидывать в валидатор
                    val parsedAddressInfo = egrnResponse.parseAddresses(d.building.coordinate!!)

                    RussiaAddressHelperPlugin.egrnResponses[d.building.osmPrimitive] =
                            Triple(d.building.coordinate, egrnResponse, parsedAddressInfo)

                    val addresses = parsedAddressInfo.getValidAddresses()
                    val isMoreThanOne = addresses.size > 1
                    var additionalNodeNumber = 1
                    val buildingCoordinate = d.building.coordinate
                    val preferredOsmAddress = parsedAddressInfo.getPreferredAddress()
                    if (preferredOsmAddress != null) {
                        val osmPrimitive = d.building.osmPrimitive
                        //костыль чтобы не присваивать адрес если есть проблемы

                        if (canAssignAddress(preferredOsmAddress) && !isMoreThanOne) {
                            if (TagSettingsReader.EGRN_ADDR_RECORD.get()) {
                                d.building.preparedTags["addr:RU:egrn"] = preferredOsmAddress.egrnAddress
                            }

                            //спорное решение - добавляем зданию адрес БЕЗ номеров квартир
                            d.building.preparedTags.plusAssign(preferredOsmAddress.getOsmAddress().getBaseAddressTags())
                            if (!osmPrimitive.hasTag("addr:housenumber")) {
                                d.building.preparedTags["source:addr"] = "ЕГРН"
                            }
                        }

                        /*  if (buildingCoordinate != null && addresses.size > 1) {
                              if (AddressNodesSettingsReader.GENERATE_ADDRESS_NODES_FOR_ADDITIONAL_ADDRESSES.get()) {
                                  addresses.filter { preferredOsmAddress.getOsmAddress().flatnumber != "" || preferredOsmAddress.getOsmAddress() != it.getOsmAddress() }
                                      .forEach {
                                          d.building.addressNodes.add(
                                              generateAddressNode(
                                                  additionalNodeNumber,
                                                  buildingCoordinate,
                                                  it
                                              )
                                          )
                                          Logging.info("Added address node $additionalNodeNumber")
                                          additionalNodeNumber++
                                      }
                              }

                          }*/
                    }
                    /*  if (parsedAddressInfo.getNonValidAddresses().isNotEmpty()) {
                          if (AddressNodesSettingsReader.GENERATE_ADDRESS_NODES_FOR_BAD_ADDRESSES.get() && buildingCoordinate != null) {
                              parsedAddressInfo.getNonValidAddresses()
                                  .forEach {
                                      d.building.addressNodes.add(
                                          generateBadAddressNode(
                                              additionalNodeNumber,
                                              buildingCoordinate,
                                              it
                                          )
                                      )
                                      additionalNodeNumber++
                                  }
                              Logging.info("Added bad address nodes, total: $additionalNodeNumber")
                          }

                          *//*   loadListener?.onNotFoundStreetParser?.invoke(
                               parsedAddressInfo.getNonValidAdressess().filter { (_, street) -> street.extractedName != "" && street.name == "" }
                                   .map { (_, street, _) -> Pair(street.extractedName, street.extractedType) }
                           )*//*
                    }*/
                }
                null
            }
        }
        return defers
    }

    private fun generateAddressNode(
        index: Int,
        startCoordinate: EastNorth,
        addressInfo: ParsedAddress
    ): Node {
        val defaultTagsForNode: Map<String, String> = mapOf("source:addr" to "ЕГРН", "fixme" to "yes")
        val node = Node(getNodePlacement(startCoordinate, index))
        addressInfo.getOsmAddress().getTags().forEach { (tagKey, tagValue) -> node.put(tagKey, tagValue) }
        defaultTagsForNode.forEach { (tagKey, tagValue) -> node.put(tagKey, tagValue) }
        node.put("addr:RU:egrn", addressInfo.egrnAddress)
        node.put("addr:RU:egrn_type", if (addressInfo.isBuildingAddress()) "BUILDING" else "PARCEL")
        return node
    }

    private fun generateBadAddressNode(
        index: Int,
        startCoordinate: EastNorth,
        badAddressInfo: ParsedAddress
    ): Node {
        val node = Node(getNodePlacement(startCoordinate, index))
        val defaultTagsForBadNode: Map<String, String> = mapOf("source:addr" to "ЕГРН", "fixme" to "REMOVE ME!")

        node.put("addr:RU:extracted_street_name", badAddressInfo.parsedStreet.extractedName)
        node.put("addr:RU:extracted_street_type", badAddressInfo.parsedStreet.extractedType)
        node.put("addr:RU:extracted_place_name", badAddressInfo.parsedPlace.extractedName)
        node.put("addr:RU:extracted_place_type", badAddressInfo.parsedPlace.extractedType)
        node.put("addr:RU:parsed_housenumber", badAddressInfo.parsedHouseNumber.housenumber)
        node.put("addr:RU:parsed_flats", badAddressInfo.parsedHouseNumber.flats)
        defaultTagsForBadNode.forEach { (tagKey, tagValue) -> node.put(tagKey, tagValue) }
        node.put("addr:RU:egrn", badAddressInfo.egrnAddress)
        node.put("addr:RU:egrn_type", if (badAddressInfo.isBuildingAddress()) "BUILDING" else "PARCEL")
        return node
    }

    private fun getNodePlacement(center: EastNorth, index: Int): EastNorth {
        //радиус разброса точек относительно центра в метрах
        val radius = 5
        val angle = 55.0
        if (index == 0) return center
        val startPoint = EastNorth(center.east() - radius, center.north())
        return startPoint.rotate(center, angle * index)
    }

    private fun sanitize() {
        items.removeAll { it.preparedTags.isEmpty() }

        if (TagSettingsReader.ENABLE_CLEAR_DOUBLE.get() && items.isNotEmpty()) {
            items = DeleteDoubles().clear(items)
        }
    }

    private fun canAssignAddress(address: ParsedAddress): Boolean {
        return !address.flags.contains(ParsingFlags.STREET_NAME_FUZZY_MATCH) &&
                !address.flags.contains(ParsingFlags.STREET_NAME_INITIALS_MATCH) &&
                !address.flags.contains(ParsingFlags.CANNOT_FIND_STREET_OBJECT_IN_OSM) &&
                !((address.flags.contains(ParsingFlags.PLACE_NAME_INITIALS_MATCH) || address.flags.contains(ParsingFlags.PLACE_NAME_FUZZY_MATCH))
                        && !address.getOsmAddress().isFilledStreetAddress())
    }
}