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
        val objectsToUpload = apiDataSet.primitives

        val needsToRemove = objectsToUpload.filter { it.hasTag("fixme", "REMOVE_ME!") }
        if (needsToRemove.isNotEmpty()) {
            val removeObjects = SequenceCommand(
                I18n.tr("Removed EGRN generated objects"),
                DeleteCommand(needsToRemove)
            )
            UndoRedoHandler.getInstance().add(removeObjects)
            apiDataSet.removeProcessed(needsToRemove)
            Logging.info("EGRN-PLUGIN Upload filter removed some unneeded objects (${needsToRemove.size})")
        }

        val discardableKeys: Collection<String> = setOf(
            "addr:RU:egrn",
            "addr:RU:egrn_type",
            "addr:RU:extracted_name",
            "addr:RU:extracted_type",
            "addr:RU:parsed_housenumber",
            "addr:RU:parsed_flats"
        )
        val needsChange = objectsToUpload.stream().flatMap { obj: OsmPrimitive -> obj.keys() }
            .anyMatch { o: String -> discardableKeys.contains(o) }
        if (needsChange) {
            val map: MutableMap<String, String?> = HashMap()
            for (key in discardableKeys) {
                map[key] = null
            }
            val removeKeys = SequenceCommand(
                I18n.tr("Removed EGRN obsolete tags"),
                ChangePropertyCommand(objectsToUpload, map)
            )
            UndoRedoHandler.getInstance().add(removeKeys)
            Logging.info("EGRN-PLUGIN Upload filter removed some unneeded tags")
        }

        return true
    }
}