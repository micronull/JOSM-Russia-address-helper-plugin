package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

import org.openstreetmap.josm.data.coor.EastNorth

interface  IParser<T> {
    /**
     * @param address Сырой адрес ЕГРН
     */
    fun parse(address: String, requestCoordinate: EastNorth): T
}