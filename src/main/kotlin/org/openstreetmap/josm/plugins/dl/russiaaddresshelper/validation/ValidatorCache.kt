package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.coor.conversion.DecimalDegreesCoordinateFormat
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.event.AbstractDatasetChangedEvent
import org.openstreetmap.josm.data.osm.event.DataSetListenerAdapter
import org.openstreetmap.josm.data.osm.event.DatasetEventManager
import org.openstreetmap.josm.data.osm.event.PrimitivesRemovedEvent
import org.openstreetmap.josm.data.projection.Projections
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.layer.LayerManager
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent
import org.openstreetmap.josm.gui.layer.OsmDataLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDResponse
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsedAddressInfo
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream


class ValidatorCache : DataSetListenerAdapter.Listener, LayerChangeListener {
    var responses: MutableMap<OsmPrimitive, ValidationRecord> = mutableMapOf()

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

    //TODO перенести экспорт данных в файлхелпер
    fun exportData(filename: String = "addressHelperExport.csv") {
        val file = File(filename)
        val append = file.exists()
        val dataToExport =
            responses.values//.filter { (_, value) -> !value.isProcessed(EGRNTestCode.EGRN_VALID_ADDRESS_ADDED) }.values
        FileOutputStream(file, append).apply { writeData(dataToExport, append) }
    }

    private fun OutputStream.writeData(records: Collection<ValidationRecord>, append: Boolean) {
        val writer = bufferedWriter()
        if (!append) {
            writer.write(""""Coordinate";"EgrnAddress";"OSMAddress";"ParsedPlace";"ParsedStreet";"ParsedHousenumber";"ParsedFlats";""" + getFlagsHeaders())
            writer.newLine()
        }

        records.forEach {
            it.addressInfo?.addresses?.forEach { address ->
                val line = "${eastNorthToLatLon(it.coordinate)};" +
                        "${address.egrnAddress.replace(";", ",")};" +
                        "${address.getOsmAddress().getInlineAddress(",")};" +
                        "${address.parsedPlace.extractedName} ${address.parsedPlace.extractedType?.name};" +
                        "${address.parsedStreet.extractedName} ${address.parsedStreet.extractedType?.name};" +
                        "${address.parsedHouseNumber.houseNumber};" +
                        " ${address.parsedHouseNumber.flats};" + getFlagsValues(address.flags)
                writer.write(line)
                writer.newLine()
            }
        }
        writer.flush()
    }

    private fun getFlagsValues(flags: MutableList<ParsingFlags>): String {
        return ParsingFlags.values().joinToString(";") { if (flags.contains(it)) "1" else "0" }
    }

    private fun getFlagsHeaders(): String {
        return ParsingFlags.values().joinToString(";") { "\"" + it.name + "\"" }
    }

    private fun eastNorthToLatLon(coord: EastNorth?): String {
        if (coord == null) return "NULL"
        val mercator = Projections.getProjectionByCode("EPSG:3857")
        val projected = mercator.eastNorth2latlonClamped(coord)

        val formatter = DecimalDegreesCoordinateFormat.INSTANCE
        val lat = formatter.latToString(projected)
        val lon = formatter.lonToString(projected)
        return "$lat,$lon"
    }

    override fun processDatasetEvent(event: AbstractDatasetChangedEvent?) {
        if (event is PrimitivesRemovedEvent) {
            primitivesRemoved(event)
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
        primitivesRemoved.forEach { responses.remove(it) }
    }

    fun initListener() {
        DatasetEventManager.getInstance()
            .addDatasetListener(dataChangedAdapter, DatasetEventManager.FireMode.IMMEDIATELY)
        MainApplication.getLayerManager().addLayerChangeListener(this)
    }
}