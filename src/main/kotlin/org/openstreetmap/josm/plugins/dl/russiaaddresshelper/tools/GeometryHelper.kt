package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.command.AddCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.*
import org.openstreetmap.josm.data.osm.visitor.paint.relations.MultipolygonCache
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.CommonSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.LayerShiftSettingsReader
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.Logging

class GeometryHelper {
    companion object {
        fun getPrimitiveCentroid(p: OsmPrimitive): EastNorth {
            return when (p) {
                is Node -> p.eastNorth
                is Way -> Geometry.getCentroid(p.nodes)
                else -> Geometry.getCentroid(
                    getBiggestClosedOuter(p as Relation)?.nodes ?: p.members.first { it.isWay }.way.nodes
                )
            }
        }

        fun getBiggestPoly(p: OsmPrimitive): Way? {
            return if (p is Way) p else getBiggestClosedOuter(p as Relation)
        }

        private fun getBiggestClosedOuter(r: Relation): Way? {
            if (r.isMultipolygon) {
                val mp = MultipolygonCache.getInstance()[r]
                return mp.outerWays.maxByOrNull { Geometry.computeArea(it) ?: 0.0 }
            }
            //неверная логика. Outer в мультике могут состоять из кусков. Площадь одного незамкнутого куска = null
            //нужно собирать outer в замкнутые контуры. Либо отказаться от сортировки полигонов мультика по площади совсем
/*            return r.members.filter { m -> m.hasRole("outer") }
                .maxByOrNull { Geometry.computeArea(it.way) }!!.way*/
            return null
        }

        fun createPolygon(
            ds: DataSet,
            coords: ArrayList<ArrayList<Double>>,
            shiftCorrect: Boolean
        ): Pair<MutableList<Command>, Way> {
            val res: MutableList<Command> = mutableListOf()
            val nodelist = coords.map { arrayList ->
                if (shiftCorrect) Node(
                    LayerShiftSettingsReader.reverseCorrection(
                        EastNorth(
                            arrayList[0],
                            arrayList[1]
                        )
                    )
                )
                else Node(EastNorth(arrayList[0], arrayList[1]))
            }.toMutableList()
            if (nodelist.first().eastNorth.equalsEpsilon(nodelist.last().eastNorth, 0.1)) {
                nodelist.removeLast()
            } else { //возможно, такую геометрию и не стоит добавлять?
                Logging.warn("Imported polygon is not closed")
            }
            nodelist.forEach { node -> res.plusAssign(AddCommand(ds, node)) }
            val way = Way()
            nodelist.plusAssign(nodelist.first())
            way.nodes = nodelist
            res.plusAssign(AddCommand(ds, way))
            return Pair(res, way)
        }

        fun getNodePlacement(center: EastNorth, index: Int): EastNorth {
            //радиус разброса точек относительно центра в метрах
            val radius = 5
            val angle = 55.0
            if (index == 0) return center
            val startPoint = EastNorth(center.east() - radius, center.north())
            return startPoint.rotate(center, angle * index)
        }

        fun getCentroidDistance(p1: OsmPrimitive, p2: OsmPrimitive): Double {
            return Geometry.getDistance(Node(getPrimitiveCentroid(p1)), Node(getPrimitiveCentroid(p2)))
        }

        /**
        проблема актуальна - для сложных зданий центроид находится вне здания и вне участка
        запрос по точке вне контура здания не возвращает данные здания
        актуальный пример, Красноярский край, Минусинск, улица Трегубенко 66А. (53.6878732, 91.6799617)
        реализован алгоритм - полигон бьется на треугольники, находим их центроид,
        если он внутри полигона здания, возвращаем его
        */

        fun getPointIntoPolygon(osmPrimitive: OsmPrimitive): EastNorth {
            return when (osmPrimitive) {
                is Way -> {
                    val centroid = Geometry.getCentroid(osmPrimitive.nodes)
                    return if (Geometry.nodeInsidePolygon(Node(centroid), osmPrimitive.nodes)) {
                        centroid
                    } else {
                        val nodes = osmPrimitive.nodes
                        for (i in 0 until nodes.size - 1) {
                            val node1 = nodes[i]
                            var j = i + 1
                            if (j > nodes.size - 1) j = j - nodes.size
                            val node2 = nodes[j]
                            var k = i + 2
                            if (j > nodes.size - 1) k = k - nodes.size
                            val node3 = nodes[k]
                            val triangleCentroid = Geometry.getCentroid(listOf(node1, node2, node3))
                            if (Geometry.nodeInsidePolygon(Node(triangleCentroid), osmPrimitive.nodes)) {
                                if (CommonSettingsReader.ENABLE_DEBUG_GEOMETRY_CREATION.get()) {
                                    val coords: ArrayList<ArrayList<Double>> = arrayListOf(
                                        arrayListOf(node1.eastNorth.east(), node1.eastNorth.north()),
                                        arrayListOf(node2.eastNorth.east(), node2.eastNorth.north()),
                                        arrayListOf(node3.eastNorth.east(), node3.eastNorth.north()),
                                    )
                                    RussiaAddressHelperPlugin.createDebugObject(coords, triangleCentroid)
                                }
                                return triangleCentroid
                            }
                        }
                        Geometry.getClosestPrimitive(Node(centroid), osmPrimitive.nodes).eastNorth
                    }
                }
                else -> {
                    //TODO реализация алгоритма поиска точки для мультиполигона
                    getPrimitiveCentroid(osmPrimitive)
                }
            }
        }
    }
}