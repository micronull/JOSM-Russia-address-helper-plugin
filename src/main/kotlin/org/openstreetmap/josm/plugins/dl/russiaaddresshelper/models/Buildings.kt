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
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EGRNFeatureType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EGRNResponse
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.AddressNodesSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.DeleteDoubles
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedStreet
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging

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
            for(building in items) {
                building.addressNodes.forEach{node ->
                cmds.add(AddCommand(ds, node))}
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

    private fun requests(loadListener: LoadListener? = null): Channel<ChannelData> {
        val limit = EgrnSettingsReader.REQUEST_LIMIT.get()
        val semaphore = kotlinx.coroutines.sync.Semaphore(limit)
        val channel = Channel<ChannelData>()

        items.mapIndexed { index, building ->
            scope.launch {
                try {
                    semaphore.acquire()

                    try {
                        val (_, response, result) = building.request()
                            .responseObject<EGRNResponse>(jacksonDeserializerOf())

                        when (result) {
                            is Result.Success -> {
                                if (!channel.isClosedForSend) {
                                    if (response.isSuccessful) {
                                        channel.send(ChannelData(building, result.value))
                                    }

                                    loadListener?.onResponse?.invoke(response)

                                    if (items.size - 1 == index) {
                                        loadListener?.onResponseContinue?.invoke()
                                        channel.close()
                                    } else if (items.size - limit >= index) {
                                        delay((EgrnSettingsReader.REQUEST_DELAY.get() * 1000).toLong())
                                    }
                                }
                            }
                            is Result.Failure -> {
                                Logging.warn(result.getException())

                                if (items.size - 1 == index) {
                                    loadListener?.onResponseContinue?.invoke()
                                    channel.close()
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Logging.warn(e.message)
                    }

                } finally {
                    if (scope.isActive) semaphore.release()
                }
            }
        }

        return channel
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
                } else if (egrnResponse.results.all { it.attrs?.address?.isBlank() != false }) {
                    Logging.info("EGRN PLUGIN no addresses found for for request $egrnResponse")
                } else {
                    val parsedAddresses = egrnResponse.parseAddresses()

                    val addresses = parsedAddresses.addresses
                    var additionalNodeNumber = 1
                    val buildingCoordinate = d.building.coordinate
                    if (addresses.isNotEmpty()) {
                        val osmPrimitive = d.building.osmPrimitive

                        //берем адрес здания если он есть, или первый попавшийся, если нет адреса здания
                        val preferredOsmAddress =
                            addresses.find { (type, _, _) -> type == EGRNFeatureType.BUILDING.type }
                                ?: addresses.first()


                        if (TagSettingsReader.EGRN_ADDR_RECORD.get()) {
                            d.building.preparedTags["addr:RU:egrn"] = preferredOsmAddress.third
                        }

                        //спорное решение - добавляем зданию адрес БЕЗ номеров квартир
                        d.building.preparedTags.plusAssign(preferredOsmAddress.second.getBaseAddressTags())
                        if (!osmPrimitive.hasTag("addr:housenumber")) {
                            d.building.preparedTags["source:addr"] = "ЕГРН"
                        }
                        if (buildingCoordinate != null) {
                            if (AddressNodesSettingsReader.GENERATE_ADDRESS_NODES_FOR_ADDITIONAL_ADDRESSES.get()) {
                                addresses.filter { (_, osmAddress, _) -> preferredOsmAddress.second.flatnumber != "" || preferredOsmAddress.second != osmAddress }
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

                        }
                    }
                    if (parsedAddresses.badAddresses.isNotEmpty()) {
                        if (AddressNodesSettingsReader.GENERATE_ADDRESS_NODES_FOR_BAD_ADDRESSES.get() && buildingCoordinate != null) {
                            parsedAddresses.badAddresses
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

                        loadListener?.onNotFoundStreetParser?.invoke(
                            parsedAddresses.badAddresses.filter { (_, street, ) -> street.first.extractedName != "" && street.second.street =="" }
                                .map { (_, street, _) -> Pair(street.first.extractedName, street.first.extractedType) }
                        )
                    }
                }
                null
            }
        }
        return defers
    }

    private fun generateAddressNode(
        index: Int,
        startCoordinate: EastNorth,
        addressInfo: Triple<Int, OSMAddress, String>
    ): Node {
        val defaultTagsForNode: Map<String, String> = mapOf("source:addr" to "ЕГРН", "fixme" to "yes")
        val node = Node(getNodePlacement(startCoordinate, index))
        addressInfo.second.getTags().forEach { (tagKey, tagValue) -> node.put(tagKey, tagValue) }
        defaultTagsForNode.forEach { (tagKey, tagValue) -> node.put(tagKey, tagValue) }
        node.put("addr:RU:egrn", addressInfo.third)
        node.put("addr:RU:egrn_type", EGRNFeatureType.fromInt(addressInfo.first).name)
        return node
    }

    private fun generateBadAddressNode(
        index: Int,
        startCoordinate: EastNorth,
        badAddressInfo: Triple<Int, Pair<ParsedStreet, OSMAddress>, String>
    ): Node {
        val node = Node(getNodePlacement(startCoordinate, index))
        val defaultTagsForBadNode: Map<String, String> = mapOf("source:addr" to "ЕГРН", "fixme" to "REMOVE ME!")

        node.put("addr:RU:extracted_name", badAddressInfo.second.first.extractedName)
        node.put("addr:RU:extracted_type", badAddressInfo.second.first.extractedType)
        node.put("addr:RU:parsed_housenumber", badAddressInfo.second.second.housenumber)
        node.put("addr:RU:parsed_flats", badAddressInfo.second.second.flatnumber)
        defaultTagsForBadNode.forEach { (tagKey, tagValue) -> node.put(tagKey, tagValue) }
        node.put("addr:RU:egrn", badAddressInfo.third)
        node.put("addr:RU:egrn_type", EGRNFeatureType.fromInt(badAddressInfo.first).name)
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
}