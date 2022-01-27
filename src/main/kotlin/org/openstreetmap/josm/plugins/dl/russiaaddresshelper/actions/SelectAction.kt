package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions

import com.github.kittinunf.fuel.core.isSuccessful
import kotlinx.coroutines.cancel
import org.openstreetmap.josm.actions.JosmAction
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.data.osm.OsmDataManager
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Buildings
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Shortcut
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

    override fun actionPerformed(e: java.awt.event.ActionEvent) {
        val dataSet: DataSet = OsmDataManager.getInstance().editDataSet ?: return
        val selected = dataSet.selected.toMutableList()

        selected.removeAll {
            it !is Way || !it.hasKey("building") || it.hasKey("fixme") || it.hasKey("addr:housenumber") || it.get("building") == "shed" || it.get("building") == "garage"
        }

        val buildings = Buildings(selected)

        if (!buildings.isNotEmpty()) {
            val msg = I18n.tr("Buildings must be selected!")
            Notification(msg).setIcon(JOptionPane.WARNING_MESSAGE).show()

            return
        }

        val listener = Buildings.LoadListener()
        val progressDialog = PleaseWaitProgressMonitor()
        var ticksCount = buildings.size
        val notFoundStreet = mutableListOf<String>()

        progressDialog.beginTask(I18n.tr("Download data"), ticksCount)
        progressDialog.showForegroundDialog()

        listener.onResponse = { response ->
            if (!response.isSuccessful) {
                ticksCount--
                progressDialog.ticksCount = ticksCount
            } else {
                progressDialog.worked(1)
            }
        }

        listener.onResponseContinue = {
            progressDialog.ticks = 0
            progressDialog.indeterminateSubTask(I18n.tr("Data processing"))
        }

        listener.onNotFoundStreetParser = {
            if (it.isNotEmpty() && !notFoundStreet.contains(it)) notFoundStreet.add(it)
        }

        listener.onComplete = { changeBuildings ->
            layerManager.editDataSet.setSelected(*changeBuildings)
            progressDialog.close()

            if (notFoundStreet.size > 0) {
                var messageNotFountStreets = "<html>Не найдены улицы:<ul>"

                notFoundStreet.forEach { messageNotFountStreets += "<li>$it</li>" }

                messageNotFountStreets += "</ul>Для перечисленных улиц адреса не загружены из ЕРГН.<br/>Необходимо отметить улицы на карте OSM.</html>"

                JOptionPane.showMessageDialog(
                    MainApplication.getMainFrame(), messageNotFountStreets, I18n.tr("Warning"), JOptionPane.WARNING_MESSAGE
                )
            }
        }

        val scope = buildings.load(listener)

        progressDialog.addCancelListener {
            progressDialog.close()
            scope.cancel("canceled")
        }
    }

}