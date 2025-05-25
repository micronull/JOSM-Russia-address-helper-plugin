package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks

import org.openstreetmap.josm.actions.upload.UploadHook
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.DeleteCommand
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.APIDataSet
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.osm.*
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging


class EGRNUploadTagFilter : UploadHook {
    override fun checkUpload(apiDataSet: APIDataSet): Boolean {

        val needsToRemove = apiDataSet.primitivesToAdd.filter {
            (it.hasTag("fixme", "REMOVE_ME!") || it.hasTag("fixme", "REMOVE ME!") ||
                (it.hasTag("fixme", "yes") && it.hasTag("source:addr", "ЕГРН"))) && it.isNew
        }
        if (needsToRemove.isNotEmpty()) {
            //удаляем данные помеченные к удалению, вместе со связанными, из датасета
            val nodesToDelete = needsToRemove.filterIsInstance<Node>().toMutableList()
            val allNodesToNotUpload = needsToRemove.filterIsInstance<Node>().toMutableList()
            val waysToDelete = needsToRemove.filterIsInstance<Way>().toMutableList()
            val relationsToDelete = needsToRemove.filterIsInstance<Relation>().toMutableList()

            relationsToDelete.forEach { rel -> rel.memberPrimitives.forEach{ primitive -> if(primitive is Node) nodesToDelete.add(primitive) else waysToDelete.add(primitive as Way)}}
            waysToDelete.forEach { way -> allNodesToNotUpload.addAll(way.nodes.distinct()) }
            if(relationsToDelete.isNotEmpty()) {
                val removeRelationsCommand = DeleteCommand.delete(relationsToDelete)
                UndoRedoHandler.getInstance().add(removeRelationsCommand)
            }
            if (waysToDelete.isNotEmpty()) {
                val removeWaysCommand = DeleteCommand.delete(waysToDelete, true, false)
                UndoRedoHandler.getInstance().add(removeWaysCommand)
            }
            if (nodesToDelete.isNotEmpty()) {
                val removeNodesCommand = DeleteCommand.delete(nodesToDelete)
                UndoRedoHandler.getInstance().add(removeNodesCommand)
            }

            //remove from upload data set
            apiDataSet.removeProcessed(allNodesToNotUpload as Collection<IPrimitive>?)
            apiDataSet.removeProcessed(waysToDelete as Collection<IPrimitive>?)
            apiDataSet.removeProcessed(relationsToDelete as Collection<IPrimitive>?)

            Logging.info("EGRN-PLUGIN Upload filter removed some unneeded objects (nodes: ${nodesToDelete.size}, ways: ${waysToDelete.size}, relations: ${relationsToDelete.size})")
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
            "egrn_name",
            "autoremove:description",
            "autoremove:loc",
            "autoremove:name",
            "autoremove:geometry:docName",
            "autoremove:source:geometry",
            "autoremove:ownershipType",
            "autoremove:permittedUseName",
            "autoremove:permittedUseByDoc"

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