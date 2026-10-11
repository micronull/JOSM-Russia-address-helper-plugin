package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.Relation
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.osm.event.*
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.layer.LayerManager
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent
import org.openstreetmap.josm.gui.layer.OsmDataLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDResponse
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsedAddressInfo
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.ValidationSettingsReader.Companion.THRESHOLD_FOR_CACHE_DIRTY_CHECK
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.GeometryHelper
import org.openstreetmap.josm.tools.Logging
import java.util.concurrent.ConcurrentHashMap


class ValidatorCache : DataSetListenerAdapter.Listener, LayerChangeListener {
    var responses: ConcurrentHashMap<OsmPrimitive, ValidationRecord> = ConcurrentHashMap()

    @Transient
    private val dataChangedAdapter = DataSetListenerAdapter(this)

    fun ignoreValidator(primitive: OsmPrimitive, code: EGRNTestCode) {
        responses.computeIfPresent(primitive) { _, value ->
            value.ignore(code)
            return@computeIfPresent value
        }
    }

    fun ignoreValidator(primitives: Collection<OsmPrimitive>, code: EGRNTestCode) {
        primitives.forEach { ignoreValidator(it, code) }
    }

    fun ignoreAllValidators(primitive: OsmPrimitive) {
        responses.computeIfPresent(primitive) { _, value ->
            value.ignoreAll()
            return@computeIfPresent value
        }
    }

    fun isIgnored(primitive: OsmPrimitive, code: EGRNTestCode): Boolean {
        return responses[primitive]?.ignored?.contains(code) ?: true
    }

    fun markProcessed(primitive: OsmPrimitive, code: EGRNTestCode) {
        responses.computeIfPresent(primitive) { _, value ->
            value.process(code)
            return@computeIfPresent value
        }
    }

    fun markProcessed(primitives: Set<OsmPrimitive>, code: EGRNTestCode) {
        primitives.forEach { markProcessed(it, code) }
    }

    fun isProcessed(primitive: OsmPrimitive, code: EGRNTestCode): Boolean {
        return responses[primitive]?.isProcessed(code) ?: false
    }

    fun emptyCache() {
        responses.clear()
    }

    fun remove(primitive: OsmPrimitive) {
        responses.remove(primitive)
    }

    fun getUnprocessed(): Map<OsmPrimitive, ValidationRecord> {
        return responses.filter { entry -> (!entry.value.isProcessed() && !entry.value.isIgnored()) }
    }

    fun getProcessed(code: EGRNTestCode): Map<OsmPrimitive, ValidationRecord> {
        return responses.filter { entry -> (entry.value.isProcessed(code)) }
    }

    fun size(): Int {
        return responses.size
    }

    fun add(primitive: OsmPrimitive, coordinate: EastNorth?, response: NSPDResponse, addressInfo: ParsedAddressInfo) {
        responses[primitive] = ValidationRecord(response, coordinate, addressInfo)
    }

    operator fun get(primitive: OsmPrimitive): ValidationRecord? {
        return responses[primitive]
    }

    fun contains(primitive: OsmPrimitive): Boolean {
        return responses.containsKey(primitive)
    }


    private fun markDirtyIfChanged(changedPrimitive: OsmPrimitive, threshold: Int) {
        if (changedPrimitive is Node) return
        val record = responses[changedPrimitive] ?: return
        if (!record.isClean) return
        if (changedPrimitive is Way || changedPrimitive is Relation) {
            val changedCoordinate = GeometryHelper.getPrimitiveCentroid(changedPrimitive)
            if (changedCoordinate.distance(record.coordinate) > threshold) {
                record.setDirty()
                Logging.debug("EGRN Plugin: primitive from cache was changed more than threshold and was marked dirty")
            }
        }
    }

    override fun processDatasetEvent(event: AbstractDatasetChangedEvent?) {
        when (event) {
            is PrimitivesRemovedEvent -> primitivesRemoved(event)
            is DataChangedEvent -> dataChangedEvent(event)
            is WayNodesChangedEvent -> wayNodesChangedEvent(event)
            is NodeMovedEvent -> nodeMovedEvent(event)
        }

    }

    private fun nodeMovedEvent(event: NodeMovedEvent) {
        val movedNode = event.node
        if (movedNode.dataSet != null) {
            movedNode.referrers.forEach {
               markDirtyIfChanged(it,THRESHOLD_FOR_CACHE_DIRTY_CHECK.get())
            }
        }
    }

    private fun wayNodesChangedEvent(event: WayNodesChangedEvent) {
        val changedWay = event.changedWay
        if (changedWay.isDeleted) {
            responses.remove(changedWay)
            return
        }

        if (changedWay != null) {
            markDirtyIfChanged(changedWay, THRESHOLD_FOR_CACHE_DIRTY_CHECK.get())
        }

        if (changedWay.dataSet != null) {
            val changedWayReferrers = changedWay.referrers
            changedWayReferrers.forEach {
                markDirtyIfChanged(it, THRESHOLD_FOR_CACHE_DIRTY_CHECK.get())
            }
        }
    }

    override fun layerAdded(e: LayerManager.LayerAddEvent?) {
        //do nothing
    }

    override fun layerRemoving(e: LayerRemoveEvent) {
        if (e.removedLayer is OsmDataLayer) {
            responses.minusAssign((e.removedLayer as OsmDataLayer).data.allNonDeletedCompletePrimitives().toSet())
        }
    }

    override fun layerOrderChanged(e: LayerManager.LayerOrderChangeEvent?) {
        //do nothing
    }

    private fun primitivesRemoved(event: PrimitivesRemovedEvent?) {
        val primitivesRemoved = event?.primitives ?: emptyList()
        primitivesRemoved.forEach {
            responses.remove(it)
        }
    }

    private fun dataChangedEvent(event: DataChangedEvent?) {
        val events = event?.events
        if (events != null) {
            events.filterIsInstance<PrimitivesRemovedEvent>()
                .forEach { primitivesRemoved(it) }
        } else {
            //mass change, need to mark all cache dirty
            dirtyAll()
        }
    }

    fun dirtyAll() {
        responses.forEach { (_, record) -> record.setDirty() }
    }

    fun initListener() {
        DatasetEventManager.getInstance()
            .addDatasetListener(dataChangedAdapter, DatasetEventManager.FireMode.IMMEDIATELY)
        //can get IllegalArgumentException if called twice. Seems its planned by JOSM devs
        MainApplication.getLayerManager().addAndFireLayerChangeListener(this)
    }
}