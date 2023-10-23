package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.PlaceTypes

class PlaceParser : IParser<ParsedPlace> {
    private val placeTypes: PlaceTypes = PlaceTypes.byYml("/references/place_types.yml")

    override fun parse(address: String, requestCoordinate: EastNorth, editDataSet: DataSet): ParsedPlace {
        // фильтруем все загруженные примитивы, основываясь на заданных в файле правилах - совпадению
        //имени с регэкспом и наличием тэгов с определенными значениями
        val allLoadedPrimitives = editDataSet.allNonDeletedCompletePrimitives()
        //на вход подается мапа <тип, map<имя, List<примитив>>
        //необходимо отфильтровать в зависимости от типа
        val primitivesToCompare: MutableMap<String, Map<String, List<OsmPrimitive>>> = mutableMapOf()
        placeTypes.types.forEach { type ->
            val foundPrimitives =
                allLoadedPrimitives.filter { p -> type.tags.all { entry -> entry.value.contains(p.get(entry.key)) } }
            val foundPrimitives2 =
                foundPrimitives.filter { StringUtils.isNotBlank(it.name) && type.hasOSMMatch(it.name) }
            val primitivesGroupedByName = foundPrimitives2.groupBy { it.name }.toMutableMap()
            val primitivesGroupedByEgrnName =
                foundPrimitives.filter { p -> p.hasTag("egrn_name") }.groupBy { it["egrn_name"] }
            primitivesGroupedByEgrnName.forEach { entry ->
                if (primitivesGroupedByName[entry.key] == null) {
                    primitivesGroupedByName.put(entry.key, entry.value)
                } else {
                    var existingPrimitives = primitivesGroupedByName[entry.key]
                    existingPrimitives = existingPrimitives!!.plus(entry.value)
                    primitivesGroupedByName.put(entry.key, existingPrimitives)
                }
            }
            primitivesToCompare.putIfAbsent(type.name, primitivesGroupedByName)
        }
        return ParsedPlace.identify(address, placeTypes, primitivesToCompare)
    }
}