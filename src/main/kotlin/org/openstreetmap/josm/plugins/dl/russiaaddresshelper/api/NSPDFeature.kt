package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import kotlinx.serialization.Contextual
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.AddressParser
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress

@kotlinx.serialization.Serializable
data class NSPDFeature(
    val id: Int,
    val type: String,
    @Contextual
    val geometry: NSPDGeometry?,
    val properties: NSPDProperties?,

    ) {
    fun getTags(prefix: String = "egrn:", filter: Set<String> = setOf()): MutableMap<String, String> {
        return properties?.getExtTags(prefix, filter) ?: mutableMapOf()
    }

    fun parseAddress(requestCoordinate: EastNorth): ParsedAddress? {
        val addressParser = AddressParser()
        val nspdAddress = this.properties?.options?.readableAddress ?: return null
        return addressParser.parse(nspdAddress, requestCoordinate, OsmDataManager.getInstance().editDataSet)
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", include = JsonTypeInfo.As.EXTERNAL_PROPERTY)
    @JsonSubTypes(
        value = [
            JsonSubTypes.Type(NSPDPolygon::class, name = "Polygon"),
            JsonSubTypes.Type(NSPDMultiPolygon::class, name = "MultiPolygon")
        ]
    )
    abstract class NSPDGeometry
}


@kotlinx.serialization.Serializable
data class NSPDPolygon(
    val type: String?,
    val coordinates: ArrayList<ArrayList<ArrayList<Double>>> = arrayListOf(),
    var crs: Crs? = Crs()
) : NSPDFeature.NSPDGeometry()

@kotlinx.serialization.Serializable
data class NSPDMultiPolygon(
    val type: String?,
    val coordinates: ArrayList<ArrayList<ArrayList<ArrayList<Double>>>> = arrayListOf(),
    var crs: Crs? = Crs()
) : NSPDFeature.NSPDGeometry()

@kotlinx.serialization.Serializable
data class Crs(
    var type: String? = null,
    var properties: CRSProperties? = CRSProperties("")
)

@kotlinx.serialization.Serializable
data class CRSProperties(var name: String?)
