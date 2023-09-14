package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.PlaceTypes

class PlaceParser : IParser<ParsedPlace> {
    private val placeTypes: PlaceTypes = PlaceTypes.byYml("/references/place_types.yml")

    override fun parse(address: String, requestCoordinate: EastNorth): ParsedPlace {
        // фильтруем все загруженные примитивы, основываясь на заданных в файле правилах - совпадению
        //имени с регэкспом и наличием тэгов с определенными значениями
        val allLoadedPrimitives = OsmDataManager.getInstance().editDataSet.allNonDeletedCompletePrimitives()
        //на вход подавается мапа <тип, map<имя, List<примитив>>
        //необходимо отфильтровать в зависимости от типа
        //если сопоставляемый примитив точка - то по расстоянию,
        //если сопоставляемый примитив - полигон (как понять?) или мультиполигон - то по вхождению точки запроса в полигон
        //фильтровать тут или ниже в identify? если там, то можно генерировать ошибки типа (место найдено но не входит/слишком далеко)
        //брать из настроек таблицу расстояний
        val primitivesToCompare: MutableMap<String, Map<String, List<OsmPrimitive>>> = mutableMapOf()
        placeTypes.types.forEach { type ->
            val foundPrimitives =
                allLoadedPrimitives.filter { p-> type.tags.all { entry -> entry.value.contains(p.get(entry.key)) } }
            val foundPrimitives2 =
                foundPrimitives.filter { StringUtils.isNotBlank(it.name) && type.hasOSMMatch(it.name) }
            var primitivesGroupedByName = foundPrimitives2.associateBy({ it.name }, { listOf(it) })
            val primitivesGroupedByEgrnName = foundPrimitives2.filter { p-> p.hasTag("egrn_name") }.associateBy ({it["egrn_name"]}, {listOf(it)})
            primitivesGroupedByName = primitivesGroupedByName.plus(primitivesGroupedByEgrnName)
            primitivesToCompare.putIfAbsent(type.name, primitivesGroupedByName)
        }
        val parsedPlace = ParsedPlace.identify(address, placeTypes, primitivesToCompare)
        //тут можно проверить сопоставление, и добавить проверки на расстояние/вхождение?

        return parsedPlace
    }
}