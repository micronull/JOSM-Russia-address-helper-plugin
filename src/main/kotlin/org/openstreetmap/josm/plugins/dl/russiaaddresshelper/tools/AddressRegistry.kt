package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.TagMap

open class AddressRegistry {
    private val records : MutableMap<String, MutableMap<String, MutableSet<OsmPrimitive>>> = mutableMapOf()
    private var totalSize : Int = 0
    private val STREET_KEY = "addr:street"
    private val PLACE_KEY = "addr:place"
    private val HOUSE_KEY = "addr:housenumber"

    fun isEmpty() : Boolean {
        return records.isEmpty()
    }

    fun clear() {
        records.clear()
        totalSize = 0
    }

    fun add (primitives : Set<OsmPrimitive> ) : Int {
        primitives.forEach(this::add)
        return totalSize
    }

    fun add (primitive : OsmPrimitive) : Boolean {
        //можно ли сделать это изящнее?
        val streetOrPlaceTag = primitive[STREET_KEY] ?: primitive[PLACE_KEY] ?: return false
        val houseTag = primitive[HOUSE_KEY] ?: return false
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
        return false
    }

    fun remove (primitives: Set<OsmPrimitive>) {
        primitives.forEach{ this.remove(it)}
    }

    fun remove ( primitive : OsmPrimitive ) : Boolean {
        if (!this.contains(primitive)) return false
        val streetOrPlaceTag = primitive[STREET_KEY] ?: primitive[PLACE_KEY] ?: return false
        val houseTag = primitive[HOUSE_KEY] ?: return false
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

    fun remove( street: String?, place: String?, housenumber: String, primitive: OsmPrimitive) : Boolean{
        val streetOrPlaceTag = street ?: place ?: return false
        return removeByStreetOrPlace(streetOrPlaceTag, housenumber, primitive)
    }

    fun contains ( primitive: OsmPrimitive ) :Boolean {
        val streetOrPlaceTag = primitive[STREET_KEY] ?: primitive[PLACE_KEY] ?: return false
        val houseTag = primitive[HOUSE_KEY] ?: return false
        return records[streetOrPlaceTag]?.get(houseTag)?.contains(primitive) ?: return false
    }

    fun hasDoubles ( primitive: OsmPrimitive ) :Boolean {
        val streetOrPlaceTag = primitive[STREET_KEY] ?: primitive[PLACE_KEY] ?: return false
        val houseTag = primitive[HOUSE_KEY] ?: return false
        val doubles = records[streetOrPlaceTag]?.get(houseTag)?: return false
        if (doubles.isEmpty() || (doubles.size == 1 && doubles.contains(primitive))) return false
        return true
    }

    fun hasDoublesWithinDistance(primitive: OsmPrimitive, distance :Double ) :Boolean {
        val doubles = getDoubles(primitive)
        return doubles.any { it != primitive && GeometryHelper.getCentroidDistance(it, primitive) < distance }
    }

    fun getDoublesWithinDistance(primitive: OsmPrimitive, distance :Double ) : Set<OsmPrimitive> {
        val doubles = getDoubles(primitive)
        return doubles.filter { it != primitive && GeometryHelper.getCentroidDistance(it, primitive) < distance }.toSet()
    }

    //должна ли эта функция возвращать сам примитив если он один?
    fun getDoubles (primitive: OsmPrimitive) : MutableSet<OsmPrimitive> {
        val streetOrPlaceTag = primitive[STREET_KEY] ?: primitive[PLACE_KEY] ?: return mutableSetOf()
        val houseTag = primitive[HOUSE_KEY] ?: return mutableSetOf()
        return records[streetOrPlaceTag]?.get(houseTag)?: return mutableSetOf()
    }

    fun getDoubles (street: String?, place: String?, housenumber: String) : MutableSet<OsmPrimitive> {
        val streetOrPlaceTag = street?:place?: return mutableSetOf()
        return records[streetOrPlaceTag]?.get(housenumber)?: return mutableSetOf()
    }

    fun contains (street: String?, place: String? = null, housenumber: String) : Boolean {
        val streetOrPlaceTag =street?:place?: return false
        return records[streetOrPlaceTag]?.get(housenumber)?.isNotEmpty() ?: false
    }

    fun getSize(): Int {
        return totalSize
    }

    fun addressValid (keys: Map<String,String>) : Boolean {
        val streetOrPlaceTag = keys[STREET_KEY] ?: keys[PLACE_KEY] ?: return false
        val houseTag = keys[HOUSE_KEY] ?: return false
        return true
    }

    fun removeByTags(keys: Map<String, String>, primitive: OsmPrimitive) :Boolean {
        return remove(keys[STREET_KEY], keys[PLACE_KEY], keys[HOUSE_KEY]!!, primitive)
    }

    protected fun addressChanged(oldKeys: Map<String, String>, newKeys: TagMap): Boolean {
        return oldKeys[HOUSE_KEY] !=newKeys[HOUSE_KEY] || oldKeys[STREET_KEY] != newKeys[STREET_KEY] || oldKeys[PLACE_KEY] != newKeys[PLACE_KEY]
    }
}