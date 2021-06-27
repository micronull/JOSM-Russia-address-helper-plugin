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
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.EgrnReader
import org.openstreetmap.josm.tools.Geometry
import org.openstreetmap.josm.tools.HttpClient
import org.openstreetmap.josm.tools.I18n
import java.awt.event.ActionEvent

class RussiaAddressHelperPluginAction : JosmAction(RussiaAddressHelperPlugin.ACTION_NAME, RussiaAddressHelperPlugin.ICON_NAME, null, null, false) {

    class DataForProcessing(val way: Way, val res: HttpClient.Response)

    @OptIn(ObsoleteCoroutinesApi::class) override fun actionPerformed(e: ActionEvent) {
        val layerManager = MainApplication.getLayerManager()
        val dataSet: DataSet = layerManager.editDataSet
        val selected = dataSet.selected.toMutableList()

        layerManager.editDataSet.setSelected()

        selected.removeAll { el -> el !is Way || !el.keys.containsKey("building") || el.keys.containsKey("fixme") || el.keys.containsKey("addr:housenumber") }

        if (selected.isNotEmpty()) {
            val progressDialog = PleaseWaitProgressMonitor()

            progressDialog.beginTask(I18n.tr("Download data"), selected.size)
            progressDialog.showForegroundDialog()

            var center: EastNorth
            val cmds: MutableList<Command> = mutableListOf()

            val limit = EgrnReader.REQUEST_LIMIT.get()
            val semaphore = kotlinx.coroutines.sync.Semaphore(limit)
            val scope = CoroutineScope(newFixedThreadPoolContext(limit, "EGRN request by limit"))
            val channel = Channel<DataForProcessing>()

            val job = scope.async {
                selected.map {
                    launch {
                        try {
                            semaphore.acquire()
                            when (it) {
                                is Way -> {
                                    center = Geometry.getCentroid(it.nodes)

                                    runCatching {
                                        EgrnQuery(center).httpClient.connect()
                                    }.onSuccess { res -> channel.send(DataForProcessing(it, res)) }
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

                repeat(selected.size) {
                    val dataForProcessing = channel.receive()

                    progressDialog.worked(1)

                    if (dataForProcessing.res.responseCode == 200) {
                        defers += scope.async {
                            runCatching {
                                dataForProcessing.res.contentReader.readText()
                            }.onSuccess {
                                val remoteResponse = StringEscapeUtils.unescapeJson(it)
                                val match = Regex("""address":\s"(.+?)"""").find(remoteResponse)
                                val way = dataForProcessing.way

                                if (match != null && !way.hasTag("fixme")) {
                                    val address = match.groupValues[1]
                                    val arAddress = address.split(",")
                                    val cmdsBeforeSize = cmds.size

                                    if (!way.hasTag("addr:housenumber")) {
                                        val houseNumber = arAddress.last().replace(Regex("""(?:дом|д\.?)\s?"""), "")

                                        cmds.add(ChangePropertyCommand(way, "addr:housenumber",houseNumber.trim()))
                                    }

                                    if (!way.hasTag("addr:street")) {
                                        val street = arAddress[arAddress.lastIndex - 1].trim().replace("ул.","улица")
                                        cmds.add(ChangePropertyCommand(way, "addr:street", street))
                                    }

                                    if (!way.hasTag("addr:full")) {
                                        cmds.add(ChangePropertyCommand(way, "addr:full", address))
                                    }

                                    if (cmdsBeforeSize < cmds.size) {
                                        cmds.add(ChangePropertyCommand(way, "fixme", "Адрес загружен из ЕГРН, требуется проверка правильности заполнения тегов."))
                                        cmds.add(ChangePropertyCommand(way, "source:addr", "ЕГРН"))
                                    }
                                }
                            }

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
            }
        }

    }
}

