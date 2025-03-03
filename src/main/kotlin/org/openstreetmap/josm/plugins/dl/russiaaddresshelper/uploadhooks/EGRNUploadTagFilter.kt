package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks

import org.openstreetmap.josm.actions.upload.UploadHook
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.DeleteCommand
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.APIDataSet
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging


class EGRNUploadTagFilter : UploadHook {
    override fun checkUpload(apiDataSet: APIDataSet): Boolean {

        val needsToRemove = apiDataSet.primitivesToAdd.filter {
            /*it is Node && */(it.hasTag("fixme", "REMOVE_ME!") || it.hasTag("fixme", "REMOVE ME!") ||
                (it.hasTag("fixme", "yes") && it.hasTag("source:addr", "ЕГРН")))
        }
        if (needsToRemove.isNotEmpty()) {
            val removeObjects = SequenceCommand(
                I18n.tr("Removed EGRN generated geometry"),
                DeleteCommand(needsToRemove)
            )

            UndoRedoHandler.getInstance().add(removeObjects)
            apiDataSet.removeProcessed(needsToRemove)

            Logging.info("EGRN-PLUGIN Upload filter removed some unneeded nodes (${needsToRemove.size})")
        }

        val discardableKeys: Collection<String> = setOf(
            "addr:RU:egrn",
            "addr:RU:egrn_type",
            "addr:RU:extracted_name",
            "addr:RU:extracted_street_name",
            "addr:RU:extracted_street_type",
            "addr:RU:extracted_place_name",
            "addr:RU:extracted_place_type",
            "addr:RU:extracted_type",
            "addr:RU:parsed_housenumber",
            "addr:RU:parsed_flats",
            "egrn_name"
        )
        val needsChange = apiDataSet.primitives.stream().flatMap { obj: OsmPrimitive -> obj.keys() }
            .anyMatch { o: String -> discardableKeys.contains(o) }
        if (needsChange) {
            val map: MutableMap<String, String?> = HashMap()
            for (key in discardableKeys) {
                map[key] = null
            }
            val removeKeys = SequenceCommand(
                I18n.tr("Removed EGRN obsolete tags"),
                ChangePropertyCommand(apiDataSet.primitives, map)
            )
            UndoRedoHandler.getInstance().add(removeKeys)
            Logging.info("EGRN-PLUGIN Upload filter removed some unneeded tags")
        }

        return true
    }
}