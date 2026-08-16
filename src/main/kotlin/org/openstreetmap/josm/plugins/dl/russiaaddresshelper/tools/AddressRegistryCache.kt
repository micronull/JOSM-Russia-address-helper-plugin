package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.data.osm.event.*
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.layer.LayerManager
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent
import org.openstreetmap.josm.gui.layer.OsmDataLayer
import org.openstreetmap.josm.tools.Logging

class AddressRegistryCache : AddressToPlacesRegistry(), DataSetListenerAdapter.Listener, LayerManager.LayerChangeListener {
    //скорее всего, будет проблема при работе с несколькими DATA слоями одновременно. Либо что-то не попадет в кэш, либо дубликаты будут искаться во всех слоях, а не в текущем
    //внутри события есть датасет, для которого оно произошло. Нужно хранить кэш адресов по всем датасетам, лол
    @Transient
    private val dataChangedAdapter = DataSetListenerAdapter(this)

    override fun processDatasetEvent(event: AbstractDatasetChangedEvent) {
        if (event is DataChangedEvent) {
            if (event.events != null) {
                event.events.forEach{processSingularEvent(it)}
            } else { //big change, no granular events created. Full refresh needed
                clear()
                if (event.dataset?.allNonDeletedCompletePrimitives() != null) {
                    this.add(event.dataset.allNonDeletedCompletePrimitives().toSet())
                } else {
                    Logging.warn("EGRN PLUGIN Address cache register processing NULL dataset")
                }
            }
        } else {
            processSingularEvent(event)
        }
    }

    private fun processSingularEvent(event: AbstractDatasetChangedEvent) {
        when (event) {
            is PrimitivesAddedEvent -> {
                this.add(event.primitives?.toSet() ?: emptySet())
            }

            is PrimitivesRemovedEvent -> {
                this.remove(event.primitives?.toSet() ?: emptySet())
            }

            is TagsChangedEvent -> {
                val newKeys = event.primitive.keys
                val oldKeys = event.originalKeys.toMap()
                if (!addressValid(oldKeys) && addressValid(newKeys)) {
                    if (this.add(event.primitive)) Logging.info("EGRN PLUGIN Address registry added ${getInlineAddress(newKeys)}")
                } else {
                    if (addressValid(oldKeys) && !addressValid(newKeys)) {
                        if (this.removeByTags(oldKeys, event.primitive)
                        ) Logging.info("EGRN PLUGIN Address registry removed ${getInlineAddress(oldKeys)}")
                    } else if (addressValid(oldKeys) && addressValid(newKeys) && addressChanged(oldKeys, newKeys)) {
                        if (this.removeByTags(oldKeys, event.primitive) &&
                            this.add(event.primitive)
                        ) Logging.info(
                            "EGRN PLUGIN Address registry removed ${getInlineAddress(oldKeys)} and added ${
                                getInlineAddress( newKeys)}"
                        )
                    } else {
                        if (!placeTagsValid(oldKeys) && placeTagsValid(newKeys)) {
                            this.add(event.primitive)
                        }
                        if (placeTagsValid(oldKeys) && !placeTagsValid(newKeys)) {
                            this.remove(event.primitive)
                        }
                    }
                }
            }

            // если изменилась граница place - удалить ее из places, потом добавить - чтобы сбросить данные о входящих в нее домиках
            //если домик перетащили - удаляем его из и добавляем его
            // более сложный вариант - отслеживать, какие домики вывалились из плейса, и удалять только их. Но неясно, как
            //проблема - event приходит на каждую ноду вэя. в результате домики удаляются и добавляются столько раз, сколько нодов в контуре
            is NodeMovedEvent -> {
                val movedNode = event.node
                if (movedNode.dataSet != null) {

                    movedNode.referrers.forEach {
                        if (isInPlacesCache(it) || isInPrimitivesCache(it)) {
                            this.remove(it)
                            this.add(it)
                        }
                    }
                } else {
                    //тут бывает DataIntegrityError - при перемещении точки контура, а потом отмене - т.е когда нода удаляется
                    Logging.warn("EGRN PLUGIN trying to do something with primitive without dataset")
                }
            }

            // обработчик - если появилась замкнутая граница place - добавить ee, если разомкнулась граница place - удалить её
            is WayNodesChangedEvent -> {
                val changedWay = event.changedWay
                if (isValidPlace(changedWay)) {
                    this.add(changedWay)
                }
                if (isInPlacesCache(changedWay) && !isValidPlace(changedWay)) {
                    this.remove(changedWay)
                }
                if (changedWay.dataSet != null) {
                    val changedWayReferrers = changedWay.referrers
                    changedWayReferrers.forEach {
                        if (isValidPlace(it)) {
                            this.add(it)
                        }
                        if (isInPlacesCache(it) && !isValidPlace(it)) {
                            this.remove(it)
                        }
                    }
                } else {
                    //ошибка DataIntegrityProblem стреляет в getRefferers в коде выше, если мы удаляем новосозданный вэй по Ctrl+Z
                    this.remove(changedWay)
                    Logging.error("Somehow, changed way is not part of dataset")
                }
            }
        }
    }

    override fun layerAdded(event: LayerAddEvent) {
        if (event.addedLayer is OsmDataLayer) {
            this.add((event.addedLayer as OsmDataLayer).data.allNonDeletedCompletePrimitives().toSet())
        }
    }

    override fun layerRemoving(event: LayerManager.LayerRemoveEvent) {
        if (event.removedLayer is OsmDataLayer) {
            this.remove((event.removedLayer as OsmDataLayer).data.allNonDeletedCompletePrimitives().toSet())
        }
    }

    override fun layerOrderChanged(event: LayerManager.LayerOrderChangeEvent) {
        //do nothing
    }

    fun initListener() {
        DatasetEventManager.getInstance()
            .addDatasetListener(dataChangedAdapter, DatasetEventManager.FireMode.IMMEDIATELY)
        MainApplication.getLayerManager().addAndFireLayerChangeListener(this)
    }

    private fun getInlineAddress(tags: Map<String, String>): String {
        return "${tags["addr:street"] ?: tags["addr:place"] ?: "NO_STREET_OR_PLACE"}, ${tags["addr:housenumber"]}"
    }

}