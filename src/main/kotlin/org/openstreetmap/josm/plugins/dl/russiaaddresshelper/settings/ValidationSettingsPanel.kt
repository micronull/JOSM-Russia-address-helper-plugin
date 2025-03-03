package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.ValidationSettingsReader
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.GridBagLayout
import javax.swing.*

class ValidationSettingsPanel : JPanel(GridBagLayout()) {

    private val distanceForStreetSearch = JosmTextField(4)
    private val distanceForPlaceNodeSearch = JosmTextField(4)
    private val stopWordsTablePanel = StopWordsTablePanel()

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        panel.add(JLabel(I18n.tr("Street way should be closer than, meters:")), GBC.std())
        panel.add(distanceForStreetSearch, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Place node should be closer than, meters:")), GBC.std())
        panel.add(distanceForPlaceNodeSearch, GBC.eop().insets(5, 0, 0, 10))

        panel.add(stopWordsTablePanel, GBC.eol())
        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    fun initFromPreferences() {
        distanceForStreetSearch.text = ValidationSettingsReader.DISTANCE_FOR_STREET_WAY_SEARCH.get().toString()
        distanceForPlaceNodeSearch.text = ValidationSettingsReader.DISTANCE_FOR_PLACE_NODE_SEARCH.get().toString()
    }


    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        try {
            var distance = Integer.valueOf(distanceForStreetSearch.text)

            if (distance <= 10) {
                distance = 10
            }

            if (distance > 10000) {
                distance = 10000
            }

            ValidationSettingsReader.DISTANCE_FOR_STREET_WAY_SEARCH.put(distance)
        } catch (e: NumberFormatException) {
            Logging.warn(e.message + "(need numeric)")
            ValidationSettingsReader.DISTANCE_FOR_STREET_WAY_SEARCH.put(200)
        }

        try {
            var distance = Integer.valueOf(distanceForPlaceNodeSearch.text)

            if (distance <= 10) {
                distance = 10
            }

            if (distance > 10000) {
                distance = 10000
            }

            ValidationSettingsReader.DISTANCE_FOR_PLACE_NODE_SEARCH.put(distance)
        } catch (e: NumberFormatException) {
            Logging.warn(e.message + "(need numeric)")
            ValidationSettingsReader.DISTANCE_FOR_PLACE_NODE_SEARCH.put(1000)
        }
        stopWordsTablePanel.saveToPreferences()
    }

}