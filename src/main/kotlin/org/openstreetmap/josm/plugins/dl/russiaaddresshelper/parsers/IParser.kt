package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet

interface  IParser<T> {
    /**
     * @param address Сырой адрес ЕГРН
     */
    fun parse(address: String, requestCoordinate: EastNorth, editDataSet: DataSet): T
}