package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions

import kotlinx.coroutines.cancel
import org.openstreetmap.josm.actions.JosmAction
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.DeleteCommand
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.command.SplitWayCommand
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.*
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.Notification.TIME_LONG
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Buildings
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.LayerShiftSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.MassActionSettingsReader
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import org.openstreetmap.josm.tools.Shortcut
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JOptionPane

class SelectAction : JosmAction(
    ACTION_NAME, ICON_NAME, null, Shortcut.registerShortcut(
        "data:egrn_selected", I18n.tr("Data: {0}", I18n.tr(ACTION_NAME)), KeyEvent.KEY_LOCATION_UNKNOWN, Shortcut.NONE
    ), false
) {
    companion object {
        val ACTION_NAME = I18n.tr("For selected objects")
        val ICON_NAME = "select.svg"
    }

    override fun updateEnabledState() {
        isEnabled = MainApplication.isDisplayingMapView() && MainApplication.getMap().mapView.isActiveLayerDrawable
    }

    override fun actionPerformed(e: ActionEvent?) {
        val dataSet: DataSet = OsmDataManager.getInstance().editDataSet ?: return
        var selectedToProcess = dataSet.selected.toList()
        var selectedWay: Way? = null
        var splittedPart: Way? = null
        val isLineSelected =
            selectedToProcess.size == 1 && selectedToProcess[0].isNew && selectedToProcess[0] is Way && !((selectedToProcess[0] as Way).isClosed) && !selectedToProcess[0].isTagged
        val buildingBadValues = MassActionSettingsReader.EGRN_MASS_ACTION_FILTER_LIST.get()
        if (isLineSelected) {
            selectedWay = selectedToProcess[0] as Way
            selectedToProcess = selectedWay.nodes.distinct()
        } else {
            selectedToProcess = selectedToProcess.filter {
                it !is Node &&
                        it.hasTag("building")
                        && buildingBadValues.all { (key, values) ->
                    values.none { tagValue ->
                        it.hasTag(
                            key,
                            tagValue
                        ) || (it.hasKey(key) && tagValue == "*")
                    }
                }
            }
        }

        if (selectedToProcess.isEmpty()) {
            val msg = I18n.tr("No selected objects to process!")
            Notification(msg).setIcon(JOptionPane.WARNING_MESSAGE).show()
            return
        }

        if (selectedToProcess.size > EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get()) {
            if (isLineSelected) {
                val msg =
                    I18n.tr("Selected way has more nodes (%s) than allowed for mass request (%s) in settings. Trying to auto-split way.")
                        .format(selectedToProcess.size, EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get().toString())

                Notification(msg).setIcon(JOptionPane.WARNING_MESSAGE).setDuration(TIME_LONG).show()
                var splitNodeId = EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get() - 1
                if (selectedToProcess.size - splitNodeId < 2) splitNodeId -= 1

                val splitCommand = SplitWayCommand.splitWay(
                    selectedWay,
                    SplitWayCommand.buildSplitChunks(selectedWay, listOf<Node>(selectedWay!!.nodes[splitNodeId])),
                    listOf(selectedWay),
                    SplitWayCommand.Strategy.keepLongestChunk(),
                    SplitWayCommand.WhenRelationOrderUncertain.ABORT
                )
                if (splitCommand.isEmpty) {
                    Notification(I18n.tr("Cannot auto-split way, operation aborted. Try to split selection way manually."))
                        .setIcon(JOptionPane.ERROR_MESSAGE).setDuration(TIME_LONG).show()
                    return
                }
                UndoRedoHandler.getInstance().add(splitCommand.get())

                val newSelection = splitCommand.get().newSelection //выбранные после разделения части
                if (newSelection.size == 2 && (newSelection[0] as Way).nodes.size <= EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get()) {
                    selectedWay = newSelection[0] as Way
                    selectedToProcess = selectedWay.nodes.distinct()
                    layerManager.editDataSet.setSelected(selectedWay)
                    splittedPart = newSelection[1] as Way
                } else {
                    Logging.error("EGRN PLUGIN: Something gone wrong when splitting selection way, cannot continue")
                    return
                }

            } else { //обрабатываем существующие здания
                selectedToProcess =
                    selectedToProcess.dropLast(selectedToProcess.size - EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get())
            }
            val msg = I18n.tr("Selected more than set limit buildings, only first %s will be processed")
                .format(EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get().toString())
            Notification(msg).setIcon(JOptionPane.WARNING_MESSAGE).show()
        }

        val shiftLayer = LayerShiftSettingsReader.getValidShiftLayer(LayerShiftSettingsReader.LAYER_SHIFT_SOURCE)
        if (shiftLayer == null) {
            val msg =
                "Shift layer doesnt set in plugin settings. Mass request without shift layer will be invalid. Aborting operation."
            val msgLoc = I18n.tr(msg)
            Notification(msgLoc).setIcon(JOptionPane.ERROR_MESSAGE).show()
            return
        } else {
            if (shiftLayer.displaySettings.displacement == EastNorth.ZERO) {
                val msg = "Shift layer doesnt have offset, this is probably an error. Change offset of layer"
                val msgLoc = I18n.tr(msg)
                Notification("$msgLoc: ${shiftLayer.name}").setIcon(JOptionPane.WARNING_MESSAGE).show()
            }
        }

        val buildings = Buildings(selectedToProcess)
        Logging.info("EGRN-PLUGIN After filtering buildings to process: ${buildings.size}")

        val listener = Buildings.LoadListener()
        val progressDialog = PleaseWaitProgressMonitor()


        val ticksCount = buildings.size
        val notFoundStreet = mutableListOf<String>()

        progressDialog.beginTask(I18n.tr("Data download"), ticksCount)
        progressDialog.showForegroundDialog()

        listener.onResponse = { response ->
            //почему-то в отладке закомментированое работает не так как без нее.
            // Видимо логика такова - если запрос был неуспешен, то мы уменьшаем общее количество запросов,
            //т.е размер прогресс бара
            //не очень понятно как именно увеличивается количество сработавших запросов.
            //   if (!response.isSuccessful) {
            //ticksCount--
            //progressDialog.ticksCount = ticksCount
            progressDialog.worked(1)
            // }
            progressDialog.setCustomText(I18n.tr("Data download") + " ${progressDialog.ticks} / ${buildings.size}")
            //  progressDialog.appendLogMessage(response.statusCode.toString())
        }

        listener.onResponseContinue = {
            progressDialog.ticks = 0
            progressDialog.indeterminateSubTask(I18n.tr("Data processing"))
        }

        listener.onNotFoundStreetParser = { list ->
            list.forEach {
                if (it.first.isNotEmpty() && !notFoundStreet.contains("\"${it.first}\" тип: \"${it.second}\"")) notFoundStreet.add(
                    "\"${it.first}\" тип: \"${it.second}\""
                )
            }
        }

        listener.onComplete = { changeBuildings ->
            if (isLineSelected) {// выделение по линии
                val cmds: MutableList<Command> = mutableListOf()
                val parentWay: Way = selectedWay!!
                //весь этот кусок - удаление линии запроса после загрузки, с учетом крайних точек, если линия была разрезана
                val startNode = parentWay.nodes.first()
                val finishNode = parentWay.nodes.last()
                val nodesToDelete: MutableSet<OsmPrimitive> = mutableSetOf()

                if (startNode != finishNode) {
                    listOf(startNode, finishNode).forEach { node ->
                        if (node.referrers.size > 1) {
                            if (node.isTagged) {
                                val referrers = node.referrers.filter { it != parentWay }
                                referrers.forEach { (it as Way).removeNode(node) }
                            } else {
                                nodesToDelete.add(node)
                            }
                        }
                    }

                    val isParentWayTooShort =
                        nodesToDelete.isNotEmpty() && (parentWay.nodes.size - nodesToDelete.size < 2)
                    if (nodesToDelete.isNotEmpty()) {
                        if (isParentWayTooShort) {
                            nodesToDelete.addAll(parentWay.nodes)
                        }
                        cmds.add(DeleteCommand.delete(nodesToDelete, false, false))
                    }

                    if (!isParentWayTooShort) {
                        cmds.add(DeleteCommand.delete(mutableListOf(parentWay), true, false))
                    }
                    UndoRedoHandler.getInstance()
                        .add(SequenceCommand(I18n.tr("Delete selection way after geometry import"), cmds))
                }
            }

            //получаем зависание, если то, что хотим выделить, попадает под фильтрацию фильтрами редактора
            //поэтому функционал выделения управляется скрытой настройкой, иначе выделение сбрасывается
            if (MassActionSettingsReader.EGRN_MASS_ACTION_SELECT_UPDATED_AFTER.get()) {
                if (isLineSelected) {
                    if (splittedPart != null) {
                        layerManager.editDataSet.setSelected(splittedPart)
                    }
                } else {
                    layerManager.editDataSet.setSelected(*changeBuildings)
                }
            } else {
                layerManager.editDataSet.clearSelection()
            }

            //валидируем все, все что у нас в кэше на данный момент и не удалено (может быть ситуация с удалением слоя в котором были уже закэшированные данные)
            val primitivesToValidate = RussiaAddressHelperPlugin.cache.responses.keys.filter { !it.isDeleted }
            RussiaAddressHelperPlugin.runEgrnValidation(primitivesToValidate)
            progressDialog.close()
        }

        val scope = buildings.load(listener)

        progressDialog.addCancelListener {
            progressDialog.close()
            scope.cancel("canceled")
        }
    }

}