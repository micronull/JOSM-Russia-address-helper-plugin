package org.openstreetmap.josm.plugins.dl.russiaaddresshelper

import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancel
import org.openstreetmap.josm.actions.JosmAction
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.models.Buildings
import org.openstreetmap.josm.tools.I18n

class NewRussiaAddressHelperPluginAction : JosmAction(RussiaAddressHelperPlugin.ACTION_NAME, RussiaAddressHelperPlugin.ICON_NAME, null, null, false) {
    @ObsoleteCoroutinesApi override fun actionPerformed(e: java.awt.event.ActionEvent) {
        val dataSet: DataSet = MainApplication.getLayerManager().editDataSet ?: return
        val buildings = Buildings(dataSet.selected.toList())

        if (!buildings.isNotEmpty()) return

        val listener = Buildings.LoadListener()
        val progressDialog = PleaseWaitProgressMonitor()
        var ticksCount = buildings.size
        val notFoundStreet = mutableListOf<String>()

        progressDialog.beginTask(I18n.tr("Download data"), ticksCount)
        progressDialog.showForegroundDialog()

        listener.onResponse = { response ->
            if (response == null || response.responseCode != 200) {
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
            if (!notFoundStreet.contains(it)) notFoundStreet.add(it)
        }

        listener.onComplete = {
            progressDialog.close()
        }

        val scope = buildings.load(listener)

        progressDialog.addCancelListener {
            progressDialog.close()
            scope.cancel("canceled")
        }
    }

}