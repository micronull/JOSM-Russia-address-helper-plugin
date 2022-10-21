package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.OsmPrimitiveType
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.StreetTypes

class StreetParser : IParser<ParsedStreet> {
    private val streetTypes: StreetTypes = StreetTypes.byYml("/references/street_types.yml")

    override fun parse(address: String): ParsedStreet {
        //тэги, откуда будут собираться возможные имена
        val altNames : List<String> = listOf("egrn_name","alt_name","old_name", "short_name")
        // Оставляем дороги у которых есть название
        // вариант - искать searchWays(Bbox) в некоей окрестности от домика/домиков
        // улицы, собранные отношениями, в которых на вэях нет тэгов??
        // так же надо искать place suburb neighbourhood
        val primitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives().filter { p ->
            p.hasKey("highway") && p.hasKey("name") && p.type.equals(OsmPrimitiveType.WAY)
        }
        val primitiveNames : MutableMap<String, String> = mutableMapOf()
        primitives.forEach {primitive ->  primitiveNames.putIfAbsent(primitive.name, primitive.name)
            altNames.map { if (primitive.hasKey(it)) {
                primitiveNames[primitive.get(it)] = primitive.name
            } }
        }

        return ParsedStreet.identify(address, streetTypes, primitiveNames)
    }
}