package org.openstreetmap.josm.plugins.dl.russiaaddresshelper

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.apache.commons.text.StringEscapeUtils
import org.openstreetmap.josm.actions.JosmAction
import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.command.SequenceCommand
import org.openstreetmap.josm.data.UndoRedoHandler
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.DataSet
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.progress.swing.PleaseWaitProgressMonitor
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EgrnQuery
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.HouseNumberParser
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.StreetParser
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.HttpClient
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.event.ActionEvent
import javax.swing.DefaultListModel
import javax.swing.JList
import javax.swing.JOptionPane
import javax.swing.JTextArea

class RussiaAddressHelperPluginAction : JosmAction(RussiaAddressHelperPlugin.ACTION_NAME, RussiaAddressHelperPlugin.ICON_NAME, null, null, false) {

    class DataForProcessing(val way: Way, val res: HttpClient.Response)

    @OptIn(ObsoleteCoroutinesApi::class) override fun actionPerformed(e: ActionEvent) {
        val layerManager = MainApplication.getLayerManager()
        val dataSet: DataSet = layerManager.editDataSet
        val selected = dataSet.selected.toMutableList()

        val notFoundStreet = mutableListOf<String>()

        layerManager.editDataSet.setSelected()

        selected.removeAll { el -> el !is Way || !el.keys.containsKey("building") || el.keys.containsKey("fixme") || el.keys.containsKey("addr:housenumber") }

        if (selected.isNotEmpty()) {
            val progressDialog = PleaseWaitProgressMonitor()

            progressDialog.beginTask(I18n.tr("Download data"), selected.size)
            progressDialog.showForegroundDialog()

            var center: EastNorth
            val cmds: MutableList<Command> = mutableListOf()

            val limit = EgrnSettingsReader.REQUEST_LIMIT.get()
            val semaphore = kotlinx.coroutines.sync.Semaphore(limit)
            val scope = CoroutineScope(newSingleThreadContext("EGRN requests"))
            val channel = Channel<DataForProcessing>()

            val job = scope.async {
                selected.mapIndexed { index, it ->
                    launch {
                        try {
                            semaphore.acquire()
                            when (it) {
                                is Way -> {
                                    center = Geometry.getCentroid(it.nodes)

                                    runCatching {
                                        EgrnQuery(center).httpClient.connect()
                                    }.onSuccess { res ->
                                        channel.send(DataForProcessing(it, res))

                                        if (selected.size - 1 == index) {
                                            channel.close()
                                        } else if (selected.size - limit >= index) {
                                            delay((EgrnSettingsReader.REQUEST_DELAY.get() * 1000).toLong())
                                        }
                                    }.onFailure {
                                        Logging.warn(it.message)

                                        if (selected.size - 1 == index) {
                                            channel.close()
                                        }
                                    }
                                }
                                else -> {
                                }
                            }
                        } finally {
                            if (scope.isActive) semaphore.release()
                        }
                    }
                }

                val defers: MutableList<Deferred<Boolean>> = mutableListOf()
                val streetParser = StreetParser()
                val houseNumberParser = HouseNumberParser()

                for (dataForProcessing in channel) {
                    progressDialog.worked(1)

                    if (dataForProcessing.res.responseCode == 200) {
                        defers += scope.async {
                            runCatching {
                                dataForProcessing.res.contentReader.readText()
                            }.onSuccess {
                                val remoteResponse = StringEscapeUtils.unescapeJson(it)
                                val match = Regex("""address":\s"(.+?)"""").find(remoteResponse)
                                val way = dataForProcessing.way

                                if (match != null) {
                                    val cmdsBeforeSize = cmds.size
                                    val address = match.groupValues[1]

                                    if (TagSettingsReader.EGRN_ADDR_RECORD.get() && !way.hasTag("addr:RU:egrn")) {
                                        cmds.add(ChangePropertyCommand(way, "addr:RU:egrn", address))
                                    }

                                    val streetParse = streetParser.parse(address)
                                    val houseNumberParse = houseNumberParser.parse(address)

                                    if (streetParse != "") {
                                        if (houseNumberParse != "") {
                                            if (!way.hasTag("addr:housenumber")) {
                                                cmds.add(ChangePropertyCommand(way, "addr:housenumber", houseNumberParse))
                                            }

                                            if (!way.hasTag("addr:street")) {
                                                cmds.add(ChangePropertyCommand(way, "addr:street", streetParse))
                                            }

                                            if (cmdsBeforeSize < cmds.size) {
                                                cmds.add(ChangePropertyCommand(way, "fixme", "Адрес загружен из ЕГРН, требуется проверка правильности заполнения тегов."))
                                                cmds.add(ChangePropertyCommand(way, "source:addr", "ЕГРН"))
                                            }
                                        }
                                    } else if (!notFoundStreet.contains(streetParser.extracted)) {
                                        notFoundStreet.add(streetParser.extracted)
                                    }
                                }
                            }.onFailure { Logging.error(it.message) }

                            true
                        }
                    }
                }

                progressDialog.ticks = 0
                progressDialog.indeterminateSubTask(I18n.tr("Data processing"))

                defers.awaitAll()
            }

            progressDialog.addCancelListener {
                progressDialog.close()
                scope.cancel("canceled")
            }

            job.invokeOnCompletion {
                progressDialog.close()
                channel.close()

                if (cmds.size > 0) {
                    val c: Command = SequenceCommand(I18n.tr("Added tags from RussiaAddressHelper "), cmds)
                    UndoRedoHandler.getInstance().add(c)
                }

                if (notFoundStreet.size > 0) {
                    var messageNotFountStreets = "<html>Ненайдены улицы:<ul>"

                    notFoundStreet.forEach { messageNotFountStreets += "<li>$it</li>" }

                    messageNotFountStreets += "</ul>Для перечисленных улиц адреса не загружены из ЕРГН.<br/>Необходимо отметить улицы на карте OSM.</html>"

                    JOptionPane.showMessageDialog(
                        MainApplication.getMainFrame(), messageNotFountStreets, I18n.tr("Warning"), JOptionPane.WARNING_MESSAGE
                    )
                }
            }
        }

    }
}

