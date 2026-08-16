package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.coor.conversion.DecimalDegreesCoordinateFormat
import org.openstreetmap.josm.data.projection.Projections
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsingFlags
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.ValidationRecord
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class FileHelper {
    companion object {
        fun getCurrentExportFilename() :String {
            val formatter = DateTimeFormatter.ofPattern("YYYY_MM_dd")
            val date = LocalDateTime.now()
            return "addressExport_${RussiaAddressHelperPlugin.versionInfo}_${formatter.format(date)}.csv"
        }

        fun exportData(filename: String = "addressHelperExport.csv", dataToExport: Collection<ValidationRecord>) {
            val file = File(filename)
            val append = file.exists()
            FileOutputStream(file, append).apply { writeData(dataToExport, append) }
        }

        private fun OutputStream.writeData(records: Collection<ValidationRecord>, append: Boolean) {
            val writer = bufferedWriter()
            if (!append) {
                writer.write(""""Coordinate";"EgrnAddress";"OSMAddress";"ParsedPlace";"ParsedStreet";"ParsedHousenumber";"ParsedFlats";""" + getFlagsHeaders())
                writer.newLine()
            }

            records.forEach {
                it.addressInfo?.addresses?.forEach { address ->
                    val line = "${eastNorthToLatLon(it.coordinate)};" +
                            "${address.egrnAddress.replace(";", ",")};" +
                            "${address.getOsmAddress().getInlineAddress(",")};" +
                            "${address.parsedPlace.extractedName} ${address.parsedPlace.extractedType?.name};" +
                            "${address.parsedStreet.extractedName} ${address.parsedStreet.extractedType?.name};" +
                            "${address.parsedHouseNumber.houseNumber};" +
                            " ${address.parsedHouseNumber.flats};" + getFlagsValues(address.flags)
                    writer.write(line)
                    writer.newLine()
                }
            }
            writer.flush()
        }

        private fun getFlagsValues(flags: MutableList<ParsingFlags>): String {
            return ParsingFlags.values().joinToString(";") { if (flags.contains(it)) "1" else "0" }
        }

        private fun getFlagsHeaders(): String {
            return ParsingFlags.values().joinToString(";") { "\"" + it.name + "\"" }
        }

        private fun eastNorthToLatLon(coord: EastNorth?): String {
            if (coord == null) return "NULL"
            val mercator = Projections.getProjectionByCode("EPSG:3857")
            val projected = mercator.eastNorth2latlonClamped(coord)

            val formatter = DecimalDegreesCoordinateFormat.INSTANCE
            val lat = formatter.latToString(projected)
            val lon = formatter.lonToString(projected)
            return "$lat,$lon"
        }
    }
}