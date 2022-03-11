package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.AddressNodesSettingsReader.Companion.GENERATE_ADDRESS_NODES_FOR_ADDITIONAL_ADDRESSES
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.AddressNodesSettingsReader.Companion.GENERATE_ADDRESS_NODES_FOR_BAD_ADDRESSES

import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.*

class AddressNodesSettingsPanel : JPanel(GridBagLayout()) {

    private val generateNodesForAdditionalAddresses =
        JCheckBox(I18n.tr("Create address nodes for other found addresses"))

    private val generateNodesForBadAddresses =
        JCheckBox(I18n.tr("Create address nodes for unparsed addresses (for debug)"))


    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)


        generateNodesForAdditionalAddresses.isSelected = GENERATE_ADDRESS_NODES_FOR_ADDITIONAL_ADDRESSES.get()
        panel.add(generateNodesForAdditionalAddresses, GBC.eol().insets(0, 0, 0, 0))

        generateNodesForBadAddresses.isSelected = GENERATE_ADDRESS_NODES_FOR_BAD_ADDRESSES.get()
        panel.add(generateNodesForBadAddresses, GBC.eol().insets(0, 0, 0, 0))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        GENERATE_ADDRESS_NODES_FOR_ADDITIONAL_ADDRESSES.put(generateNodesForAdditionalAddresses.isSelected)
        GENERATE_ADDRESS_NODES_FOR_BAD_ADDRESSES.put(generateNodesForBadAddresses.isSelected)
    }

}