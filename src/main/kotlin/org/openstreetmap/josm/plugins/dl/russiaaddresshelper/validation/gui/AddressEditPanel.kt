package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.gui

import org.apache.commons.lang3.StringUtils
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress
import org.openstreetmap.josm.tools.GBC
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class AddressEditPanel(
    p: OsmPrimitive,
    addresses: List<ParsedAddress>
) :
    JPanel(GridBagLayout()) {

    private val osmStreetNameEditBox = JosmTextField("")
    private val osmPlaceNameEditBox = JosmTextField("")
    private val osmNumberEditBox = JosmTextField("")
    private val rawAddressLabel = JMultilineLabel("")
    private val result = JLabel("")

    init {
        osmStreetNameEditBox.text = ""
        osmPlaceNameEditBox.text = ""
        osmNumberEditBox.text = ""
        val preferredHeight = 20
        osmStreetNameEditBox.preferredSize = Dimension(100, preferredHeight)
        osmPlaceNameEditBox.preferredSize = Dimension(100, preferredHeight)
        osmNumberEditBox.preferredSize = Dimension(100, preferredHeight)
        addresses.forEach {
            setPanelData(it)
        }
        rawAddressLabel.text = "Полученные адреса:<br>" + getEgrnAdresses(addresses) + "<br><br> Распознанные поля:"
        rawAddressLabel.setMaxWidth(800)
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        panel.add(rawAddressLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL))
        panel.add(JLabel("addr:street"), GBC.std().anchor(GBC.WEST))
        panel.add(osmStreetNameEditBox, GBC.eol().fill(GBC.HORIZONTAL).insets(10, 10, 10, 0))
        panel.add(JLabel("addr:place"), GBC.std().anchor(GBC.WEST))
        panel.add(osmPlaceNameEditBox, GBC.eol().fill(GBC.HORIZONTAL).insets(10, 10, 10, 0))
        panel.add(JLabel("addr:housenumber"), GBC.std().anchor(GBC.WEST))
        panel.add(osmNumberEditBox, GBC.eop().fill(GBC.HORIZONTAL).insets(10, 10, 10, 0))
        panel.add(result, GBC.eop())

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())

        osmNumberEditBox.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) =
                checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)

            override fun removeUpdate(e: DocumentEvent?) =
                checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)

            override fun changedUpdate(e: DocumentEvent?) =
                checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)
        })

        osmPlaceNameEditBox.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) =
                checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)

            override fun removeUpdate(e: DocumentEvent?) =
                checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)

            override fun changedUpdate(e: DocumentEvent?) =
                checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)
        })

        osmStreetNameEditBox.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent?) =
                checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)

            override fun removeUpdate(e: DocumentEvent?) =
                checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)

            override fun changedUpdate(e: DocumentEvent?) =
                checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)
        })
        checkForDoubles(osmPlaceNameEditBox, osmStreetNameEditBox, osmNumberEditBox, p)
    }

    private fun setPanelData(preferredAddress: ParsedAddress) {
        if (StringUtils.isNotBlank(preferredAddress.parsedStreet.name)) {
            osmStreetNameEditBox.text = preferredAddress.parsedStreet.name
        }
        if (StringUtils.isNotBlank(preferredAddress.parsedPlace.name)) {
            osmPlaceNameEditBox.text = preferredAddress.parsedPlace.name
        }
        if (StringUtils.isNotBlank(preferredAddress.parsedHouseNumber.houseNumber)) {
            osmNumberEditBox.text = preferredAddress.parsedHouseNumber.houseNumber
        }
    }

    fun getStreetName(): String {
        return osmStreetNameEditBox.text
    }

    fun getPlaceName(): String {
        return osmPlaceNameEditBox.text
    }

    fun getHouseNumber(): String {
        return osmNumberEditBox.text
    }

    private fun checkForDoubles(
        osmPlaceNameEditBox: JosmTextField,
        osmStreetNameEditBox: JosmTextField,
        osmNumberEditBox: JosmTextField,
        primitive: OsmPrimitive
    ) {
        val placeName = osmPlaceNameEditBox.text
        val streetName = osmStreetNameEditBox.text
        val houseNumber = osmNumberEditBox.text

        val hasPlaceDouble = if (!placeName.isNullOrBlank() && !houseNumber.isNullOrBlank()) {
            RussiaAddressHelperPlugin.addressRegistry.getDoubles(
                primitive,
                mapOf(Pair("addr:place", placeName), Pair("addr:housenumber", houseNumber))
            ).isNotEmpty()
        } else false
        val hasStreetDouble = if (!streetName.isNullOrBlank() && !houseNumber.isNullOrBlank()) {
            RussiaAddressHelperPlugin.addressRegistry.getDoubles(
                primitive,
                mapOf(Pair("addr:street", streetName), Pair("addr:housenumber", houseNumber))
            ).isNotEmpty()
        } else false
        if (hasPlaceDouble) {
            osmPlaceNameEditBox.background = Color.PINK
        } else {
            osmPlaceNameEditBox.background = Color.WHITE
        }
        if (hasStreetDouble) {
            osmStreetNameEditBox.background = Color.PINK
        } else {
            osmStreetNameEditBox.background = Color.WHITE
        }

        if (hasStreetDouble || hasPlaceDouble) {
            osmNumberEditBox.background = Color.PINK
        } else {
            osmNumberEditBox.background = Color.WHITE
        }
        val streetOrPlace = if (!streetName.isNullOrBlank()) {
            streetName
        } else {
            placeName
        }
        result.text = "Будет присвоен адрес: " + if (!streetOrPlace.isNullOrBlank() && !houseNumber.isNullOrBlank()) {
            "$streetOrPlace, $houseNumber"

        } else {
            "Адрес не будет присвоен"

        }
    }

    private fun getEgrnAdresses(addresses: List<ParsedAddress>): String {
        return addresses.joinToString("<br>") { "${it.egrnAddress},<b> тип: ${if (it.isBuildingAddress()) "здание" else "участок"}</b>" }
    }

}