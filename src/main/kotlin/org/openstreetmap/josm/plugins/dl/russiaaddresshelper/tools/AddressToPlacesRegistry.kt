package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.data.osm.*
import org.openstreetmap.josm.data.osm.visitor.paint.relations.MultipolygonCache
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.ValidationSettingsReader
import org.openstreetmap.josm.tools.Geometry.*

open class AddressToPlacesRegistry {
    //private val records : MutableMap<OsmPrimitive?,MutableMap<String, MutableMap<String, MutableSet<OsmPrimitive>>>> = mutableMapOf()
    private val records: MutableMap<String, MutableMap<String, MutableSet<OsmPrimitive>>> = mutableMapOf()

    //мапа примитивов, в значениях которых - минимальный по иерархии (по площади?) мультполигон или полигон границы места, внутри которого находится этот примитив
    private val primitivesToPlaces: MutableMap<OsmPrimitive, OsmPrimitive> = mutableMapOf()
    private val placesToPrimitives: MutableMap<OsmPrimitive, MutableSet<OsmPrimitive>> = mutableMapOf()

    private var totalSize: Int = 0
    private val ADDR_STREET_KEY = "addr:street"
    private val ADDR_PLACE_KEY = "addr:place"
    private val ADDR_HOUSE_KEY = "addr:housenumber"
    private val PLACE_KEY = "place"
    private val NAME_KEY = "name"

    fun isEmpty(): Boolean {
        return records.isEmpty()
    }

    fun clear() {
        records.clear()
        primitivesToPlaces.clear()
        placesToPrimitives.clear()
        totalSize = 0
    }

    fun add(primitives: Set<OsmPrimitive>): Int {
        primitives.forEach(this::add)
        return totalSize
    }

    fun add(primitive: OsmPrimitive): Boolean {
        //это место. нужно ли игнорить точки места?
        if (isValidPlace(primitive)) {
            placesToPrimitives.putIfAbsent(primitive, mutableSetOf())
            return true
        } else {
            if (ignoreNonBuildings(primitive)) return false
            if (ignoreNodes(primitive)) return false

            //можно ли сделать это изящнее?
            val streetOrPlaceTag = primitive[ADDR_STREET_KEY] ?: primitive[ADDR_PLACE_KEY] ?: return false

            val houseTag = primitive[ADDR_HOUSE_KEY] ?: return false
            val houses = if (records.containsKey(streetOrPlaceTag))
                records[streetOrPlaceTag]
            else {
                records[streetOrPlaceTag] = mutableMapOf(houseTag to mutableSetOf(primitive))
                totalSize++
                return true
            }
            val primitives = if (houses?.containsKey(houseTag) == true)
                houses[houseTag]
            else {
                houses?.set(houseTag, mutableSetOf(primitive))
                totalSize++
                return true
            }
            if (primitives?.add(primitive) == true) {
                totalSize++
                return true
            }
            //примитив уже в списке, добавление не требуется
            return false
        }
    }

    private fun ignoreNonBuildings(primitive: OsmPrimitive): Boolean {
        return !primitive.hasKey("building") && !primitive.hasKey("building:part")
    }

    private fun ignoreNodes(primitive: OsmPrimitive): Boolean {
        return primitive is Node && (!isBuildingAddress(primitive) || ValidationSettingsReader.IGNORE_ADDRESS_NODES.get())
    }

    private fun isBuildingAddress(primitive: OsmPrimitive): Boolean {
       return primitive.hasKey("building") && primitive.hasKey(ADDR_HOUSE_KEY) && (primitive.hasKey(ADDR_STREET_KEY) || primitive.hasKey(ADDR_PLACE_KEY))
    }

    fun remove(primitives: Set<OsmPrimitive>) {
        primitives.forEach { this.remove(it) }
    }

    fun remove(primitive: OsmPrimitive): Boolean {
        if (placesToPrimitives.containsKey(primitive)) { //это граница place
            val placePrimitives = placesToPrimitives.remove(primitive)
            placePrimitives?.forEach{primitivesToPlaces.remove(it)}
            return true
        }
        val primitivePlace = primitivesToPlaces.remove(primitive)
        if (primitivePlace != null) {
            placesToPrimitives[primitivePlace]?.remove(primitive)
        }
        val streetOrPlaceTag = primitive[ADDR_STREET_KEY] ?: primitive[ADDR_PLACE_KEY] ?: return false
        val houseTag = primitive[ADDR_HOUSE_KEY] ?: return false
        return removeByStreetOrPlace(streetOrPlaceTag, houseTag, primitive)
    }

    private fun removeByStreetOrPlace(
        streetOrPlaceTag: String,
        houseTag: String,
        primitive: OsmPrimitive
    ): Boolean {
        val houses = records[streetOrPlaceTag] ?: return false
        val primitives = houses[houseTag] ?: return false
        if (primitives.remove(primitive)) {
            if (primitives.isEmpty()) {
                houses.remove(houseTag)
            }
            if (houses.isEmpty()) {
                records.remove(streetOrPlaceTag)
            }
            totalSize--
            return true
        }
        return false
    }

    fun remove(street: String?, place: String?, housenumber: String, primitive: OsmPrimitive): Boolean {
        val primitivePlace = primitivesToPlaces.remove(primitive)
        if (primitivePlace != null) {
            placesToPrimitives[primitivePlace]?.remove(primitive)
        }
        val streetOrPlaceTag = street ?: place ?: return false
        return removeByStreetOrPlace(streetOrPlaceTag, housenumber, primitive)
    }

    fun contains(primitive: OsmPrimitive): Boolean {
        val streetOrPlaceTag = primitive[ADDR_STREET_KEY] ?: primitive[ADDR_PLACE_KEY] ?: return false
        val houseTag = primitive[ADDR_HOUSE_KEY] ?: return false
        return records[streetOrPlaceTag]?.get(houseTag)?.contains(primitive) ?: return false
    }

    //должна ли эта функция возвращать сам примитив если он один? нет
    fun getDoubles(primitive: OsmPrimitive, preparedTags: Map<String,String> = mutableMapOf()): MutableSet<OsmPrimitive> {
        val distance = CommonSettingsReader.CLEAR_DOUBLE_DISTANCE.get().toDouble()
        val streetOrPlaceTag = preparedTags[ADDR_STREET_KEY] ?: preparedTags[ADDR_PLACE_KEY] ?: primitive[ADDR_STREET_KEY] ?: primitive[ADDR_PLACE_KEY] ?: return mutableSetOf()
        val houseTag = preparedTags[ADDR_HOUSE_KEY] ?: primitive[ADDR_HOUSE_KEY] ?: return mutableSetOf()
        val potentialDoubles = records[streetOrPlaceTag]?.get(houseTag)
        if (potentialDoubles.isNullOrEmpty()) return mutableSetOf()
        return potentialDoubles.filter { it != primitive && primitivesInSamePlace(it, primitive, distance) }.toMutableSet()
    }

    fun getPlaceForBuilding(primitive: OsmPrimitive): OsmPrimitive? {
        if (primitivesToPlaces.contains(primitive)) return primitivesToPlaces[primitive]

        var insidePlaces = placesToPrimitives.keys.filter { place -> isInside(primitive, place) }
        if (insidePlaces.isEmpty()) return null
        if (insidePlaces.size > 1) {
            //more than 1 place found - need to find the lowest one
            insidePlaces = insidePlaces.sortedWith(placesComparator)
        }
        val primitivePlace = insidePlaces[0]
        putPrimitiveToPlace(primitive, primitivePlace)
        return primitivePlace
    }

    private val PLACETYPE : List<String> = listOf("locality","isolated_dwelling", "neighbourhood", "suburb", "hamlet", "village", "town")
    private val placesComparator = Comparator{ p1: OsmPrimitive, p2: OsmPrimitive -> PLACETYPE.indexOf(p1["place"]) - PLACETYPE.indexOf(p2["place"]) }


   fun putPrimitiveToPlace(
       primitive: OsmPrimitive,
       primitivePlace: OsmPrimitive
    ) {
        if (placesToPrimitives[primitivePlace] == null) {
            placesToPrimitives[primitivePlace] = mutableSetOf(primitive)
        } else {
            placesToPrimitives[primitivePlace]?.plusAssign(primitive)
        }
        primitivesToPlaces[primitive] = primitivePlace
    }

    private fun isInside(primitive: OsmPrimitive, place: OsmPrimitive): Boolean {
        if (place is Way || place is Relation) {
            val primitivesInside = filterInsideAnyPolygon( listOf(primitive), place)
            return primitivesInside.isNotEmpty()
        }/*
            return if (primitive is Node) {
                nodeInsidePolygon(primitive, place.nodes)
            } else
                polygonIntersection(
                    getArea(GeometryHelper.getBiggestPoly(primitive)!!.nodes),
                    getArea(place.nodes)
                ).equals(PolygonIntersection.FIRST_INSIDE_SECOND)
        } else if (place is Relation) {
            return if (primitive is Node) {
                isNodeInsideMultiPolygon(primitive as Node, place, null)
            } else
                //эта проверка (get Biggest Poly) дает NPE если мы загрузили мультиполигоновую геометрию здания которой пока нет в кэше мультиполигонов
                //isPolygonInsideMultiPolygon(GeometryHelper.getBiggestPoly(primitive)!!.nodes, place, null)
                filterInsideAnyPolygon()
        }*/
        return false
    }

    private fun primitivesInSamePlace(
        firstPrimitive: OsmPrimitive,
        secondPrimitive: OsmPrimitive,
        distance: Double
    ): Boolean {
        val placeOfFirstPrimitive: OsmPrimitive? = getPlaceForBuilding(firstPrimitive)
        val placeOfSecondPrimitive: OsmPrimitive? = getPlaceForBuilding(secondPrimitive)
        return (placeOfFirstPrimitive == null && placeOfSecondPrimitive == null && GeometryHelper.getCentroidDistance(
            firstPrimitive, secondPrimitive ) < distance)
                || (placeOfFirstPrimitive == placeOfSecondPrimitive)
    }

/*    fun getDoubles(street: String?, place: String?, housenumber: String): MutableSet<OsmPrimitive> {
        val streetOrPlaceTag = street ?: place ?: return mutableSetOf()
        return records[streetOrPlaceTag]?.get(housenumber) ?: return mutableSetOf()
    }*/

    fun contains(street: String?, place: String? = null, housenumber: String): Boolean {
        val streetOrPlaceTag = street ?: place ?: return false
        return records[streetOrPlaceTag]?.get(housenumber)?.isNotEmpty() ?: false
    }

    fun getSize(): Int {
        return totalSize
    }

    fun hasAddress(keys: Map<String, String>): Boolean {
        val streetOrPlaceTag = keys[ADDR_STREET_KEY] ?: keys[ADDR_PLACE_KEY] ?: return false
        val houseTag = keys[ADDR_HOUSE_KEY] ?: return false
        return true
    }

    fun removeByTags(keys: Map<String, String>, primitive: OsmPrimitive): Boolean {
        return remove(keys[ADDR_STREET_KEY], keys[ADDR_PLACE_KEY], keys[ADDR_HOUSE_KEY]!!, primitive)
    }

    protected fun addressChanged(oldKeys: Map<String, String>, newKeys: TagMap): Boolean {
        return oldKeys[ADDR_HOUSE_KEY] != newKeys[ADDR_HOUSE_KEY] || oldKeys[ADDR_STREET_KEY] != newKeys[ADDR_STREET_KEY] || oldKeys[ADDR_PLACE_KEY] != newKeys[ADDR_PLACE_KEY]
    }

    fun isValidPlace(primitive : OsmPrimitive?) :Boolean {
        if (primitive == null) return false
        return primitive[PLACE_KEY] != null && primitive[NAME_KEY] != null && ((primitive is Way && primitive.isClosed)
                || (primitive.isMultipolygon && !primitive.isIncomplete && MultipolygonCache.getInstance().get(primitive as Relation).openEnds.isEmpty()))
    }

    fun hasPlaceTags(primitive : OsmPrimitive?) :Boolean {
        if (primitive == null) return false
        return primitive[PLACE_KEY] != null && primitive[NAME_KEY] != null
    }

    fun addressValid (keys: Map<String,String>) : Boolean {
        val streetOrPlaceTag = keys[ADDR_STREET_KEY] ?: keys[ADDR_PLACE_KEY] ?: return false
        val houseTag = keys[ADDR_HOUSE_KEY] ?: return false
        return true
    }

    fun placeTagsValid(keys: Map<String,String>) :Boolean {
        return keys[PLACE_KEY] != null && keys[NAME_KEY] != null
    }

    fun placeTagsChanged (oldKeys: Map<String, String>, newKeys: TagMap): Boolean {
        return oldKeys[PLACE_KEY] != newKeys[PLACE_KEY]
    }

    fun isInPlacesCache( primitive: OsmPrimitive) : Boolean {
        return placesToPrimitives.containsKey(primitive)
    }

    fun isInPrimitivesCache (primitive: OsmPrimitive) :Boolean {
        return primitivesToPlaces.containsKey(primitive)
    }

    fun getPlacesCount(): Int {
        return placesToPrimitives.size
    }

    fun getPrimitivesCount(): Int {
        return primitivesToPlaces.size
    }
}