package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel

class EgrnRequestSettingsPanel : JPanel(GridBagLayout()) {
    private val egrnUrl = JosmTextField()
    private val egrnRequestLimit = JosmTextField(3)
    private val egrnRequestDelay = JosmTextField(3)

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        panel.add(JLabel(I18n.tr("EGRN request url:")), GBC.std())
        panel.add(egrnUrl, GBC.eop().fill(GBC.HORIZONTAL).insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Request limit (from 1 to 10):")), GBC.std())
        panel.add(egrnRequestLimit, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Delay between requests in seconds:")), GBC.std())
        panel.add(egrnRequestDelay, GBC.eop().insets(5, 0, 0, 5))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Initializes the panel from preferences
     */
    fun initFromPreferences() {
        egrnUrl.text = EgrnSettingsReader.EGRN_URL_REQUEST.get()
        egrnRequestLimit.text = EgrnSettingsReader.REQUEST_LIMIT.get().toString()
        egrnRequestDelay.text = EgrnSettingsReader.REQUEST_DELAY.get().toString()
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        EgrnSettingsReader.EGRN_URL_REQUEST.put(egrnUrl.text)

        try {
            var limit = Integer.valueOf(egrnRequestLimit.text)

            if (limit <= 0) {
                limit = 1
            }

            if (limit > 10) {
                limit = 10
            }

            EgrnSettingsReader.REQUEST_LIMIT.put(limit)
        } catch (e: NumberFormatException) {
            Logging.warn(e.message + "(need numeric)")
        }

        try {
            var delay = Integer.valueOf(egrnRequestDelay.text)

            if (delay <= 0) {
                delay = 1
            }

            EgrnSettingsReader.REQUEST_DELAY.put(delay)
        } catch (e: NumberFormatException) {
            Logging.warn(e.message + "(need numeric)")
        }
    }
}