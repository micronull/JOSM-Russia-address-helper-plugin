package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.OsmPrimitiveType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetTypes

class StreetParser : IParser<ParsedStreet> {
    private val streetTypes: StreetTypes = StreetTypes.byYml("/references/street_types.yml")

    override fun parse(address: String, requestCoordinate: EastNorth): ParsedStreet {
        //тэги, откуда будут собираться возможные имена
        val altNames: List<String> = listOf("egrn_name", "alt_name", "old_name", "short_name")
        // Оставляем дороги у которых есть название
        // вариант - искать searchWays(Bbox) в некоей окрестности от домика/домиков
        // улицы, собранные отношениями, в которых на вэях нет тэгов??

        val primitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives().filter { p ->
            p.hasKey("highway") && p.hasKey("name") && p.type.equals(OsmPrimitiveType.WAY)
        }

        //формируем мапу <название вэя (в том числе из альтернативных тэгов), название из name, список обьектов
        val primitiveNames: MutableMap<String, Pair<String, List<OsmPrimitive>>> = mutableMapOf()
        val primitivesByName = primitives.groupBy({ it.name }, { it })
        primitivesByName.forEach { (name, primitivesList) ->
            primitiveNames.putIfAbsent(name, Pair(name, primitivesList))
            primitivesList.forEach { primitive->
                altNames.map {
                    if (primitive.hasKey(it)) {
                        val altName = primitive.get(it)
                        primitiveNames.putIfAbsent(altName,Pair(primitive.name, primitivesList.filter {pr ->  pr.hasTag(it, altName) }))
                    }
                }
            }
        }

        return ParsedStreet.identify(address, streetTypes, primitiveNames)
    }
}