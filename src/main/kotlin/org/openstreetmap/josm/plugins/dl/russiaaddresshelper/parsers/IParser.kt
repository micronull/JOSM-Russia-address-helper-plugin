package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers

interface IParser<T> {
    /**
     * @param address Сырой адрес ЕГРН
     */
    fun parse(address: String): T
}