package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.event.*
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.layer.LayerManager
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent
import org.openstreetmap.josm.gui.layer.OsmDataLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDResponse
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsedAddressInfo
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

    fun get(primitive: OsmPrimitive): ValidationRecord? {
        return responses[primitive]
    }

    fun contains(primitive: OsmPrimitive): Boolean {
        return responses.containsKey(primitive)
    }


    override fun processDatasetEvent(event: AbstractDatasetChangedEvent?) {
        when (event) {
            is PrimitivesRemovedEvent -> primitivesRemoved(event)
            is DataChangedEvent -> dataChangedEvent(event)
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
        val events = event?.events ?: emptyList()
        events.filter { it is PrimitivesRemovedEvent }
            .forEach { primitivesRemoved(it as PrimitivesRemovedEvent) }
    }

    fun initListener() {
        DatasetEventManager.getInstance()
            .addDatasetListener(dataChangedAdapter, DatasetEventManager.FireMode.IMMEDIATELY)
        //can get IllegalArgumentException if called twice. Seems its planned by JOSM devs
        MainApplication.getLayerManager().addAndFireLayerChangeListener(this)
    }
}