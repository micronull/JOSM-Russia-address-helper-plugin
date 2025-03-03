package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools

import org.openstreetmap.josm.data.osm.event.*
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.layer.LayerManager
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent
import org.openstreetmap.josm.gui.layer.OsmDataLayer
import org.openstreetmap.josm.tools.Logging

class AddressRegistryCache : AddressRegistry(), DataSetListenerAdapter.Listener, LayerManager.LayerChangeListener {

    @Transient
    private val dataChangedAdapter = DataSetListenerAdapter(this)

    override fun processDatasetEvent(event: AbstractDatasetChangedEvent) {

        if (event is PrimitivesAddedEvent) {
            this.add(event.primitives?.toSet() ?: emptySet())
        }

        if (event is PrimitivesRemovedEvent) {
            this.remove(event.primitives?.toSet() ?: emptySet())
        }

        if (event is TagsChangedEvent) {
            val newKeys = event.primitive.keys
            val oldKeys = event.originalKeys.toMap()
            if (!addressValid(oldKeys) && addressValid(newKeys)) {
                if (this.add(event.primitive)) Logging.info(
                    "EGRN PLUGIN Address registry added ${
                        getInlineAddress(
                            newKeys
                        )
                    }"
                )
            } else
                if (addressValid(oldKeys) && !addressValid(newKeys)) {
                    if (this.removeByTags(
                            oldKeys,
                            event.primitive
                        )
                    ) Logging.info("EGRN PLUGIN Address registry removed ${getInlineAddress(newKeys)}")
                } else if (addressValid(oldKeys) && addressValid(newKeys) && addressChanged(oldKeys, newKeys)) {
                    if (this.removeByTags(oldKeys, event.primitive) &&
                        this.add(event.primitive)
                    ) Logging.info(
                        "EGRN PLUGIN Address registry removed ${getInlineAddress(oldKeys)} and added ${
                            getInlineAddress(
                                newKeys
                            )
                        }"
                    )
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
        MainApplication.getLayerManager().addLayerChangeListener(this)
    }

    private fun getInlineAddress(tags: Map<String, String>): String {
        return "${tags["addr:street"] ?: tags["addr:place"] ?: "NO_STREET_OR_PLACE"}, ${tags["addr:housenumber"]}"
    }

}