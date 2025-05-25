package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions

import kotlinx.coroutines.cancel
import org.openstreetmap.josm.actions.JosmAction
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.data.osm.Node
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
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
        var selected = dataSet.selected.toList()
        val buildingBadValues = MassActionSettingsReader.EGRN_MASS_ACTION_FILTER_LIST.get()
        selected = selected.filter {
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

        if (selected.isEmpty()) {
            val msg = I18n.tr("All selected buildings are not eligible for request!")
            Notification(msg).setIcon(JOptionPane.WARNING_MESSAGE).show()
            return
        }

        if (selected.size > EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get()) {
            selected = selected.dropLast(selected.size - EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get())
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

        val buildings = Buildings(selected)
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
            //получаем зависание, если то, что хотим выделить, попадает под фильтрацию фильтрами редактора
            //поэтому сбрасываем выделение совсем
            if (MassActionSettingsReader.EGRN_MASS_ACTION_SELECT_UPDATED_AFTER.get()) {
                layerManager.editDataSet.setSelected(*changeBuildings)
            }
            else {
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