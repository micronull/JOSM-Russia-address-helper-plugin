package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.Logging
import java.awt.GridBagLayout
import javax.swing.*

class EgrnRequestSettingsPanel : JPanel(GridBagLayout()) {
    private val egrnUrl = JosmTextField()
    private val userAgent = JosmTextField()
    private val egrnRequestLimit = JosmTextField(3)
    private val egrnRequestSelectionLimit = JosmTextField(3)
    private val egrnTotalRequestsCounter = JosmTextField(3)
    private val egrnRequestDelay = JosmTextField(3)
    private val disableSSLforRequests = JCheckBox(I18n.tr("Disable SSL for EGRN requests"))

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        panel.add(disableSSLforRequests, GBC.eol())

        panel.add(JLabel(I18n.tr("EGRN request url:")), GBC.std())
        panel.add(egrnUrl, GBC.eop().fill(GBC.HORIZONTAL).insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("EGRN request user agent string:")), GBC.std())
        panel.add(userAgent, GBC.eop().fill(GBC.HORIZONTAL).insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Request limit (from 1 to 10):")), GBC.std())
        panel.add(egrnRequestLimit, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Request limit for single select action:")), GBC.std())
        panel.add(egrnRequestSelectionLimit, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Delay between requests in seconds:")), GBC.std())
        panel.add(egrnRequestDelay, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Total requests to EGRN in current session:")), GBC.std())
        panel.add(egrnTotalRequestsCounter, GBC.eop().insets(5, 0, 0, 5))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Initializes the panel from preferences
     */
    fun initFromPreferences() {
        egrnUrl.text = EgrnSettingsReader.EGRN_URL_REQUEST.get()
        userAgent.text = EgrnSettingsReader.EGRN_REQUEST_USER_AGENT.get()
        egrnRequestLimit.text = EgrnSettingsReader.REQUEST_LIMIT.get().toString()
        egrnRequestSelectionLimit.text = EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get().toString()
        egrnRequestDelay.text = EgrnSettingsReader.REQUEST_DELAY.get().toString()
        disableSSLforRequests.isSelected = EgrnSettingsReader.EGRN_DISABLE_SSL_FOR_REQUEST.get()
        egrnTotalRequestsCounter.text = RussiaAddressHelperPlugin.totalRequestsPerSession.toString()
        egrnTotalRequestsCounter.isEnabled = false
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        EgrnSettingsReader.EGRN_URL_REQUEST.put(egrnUrl.text)
        EgrnSettingsReader.EGRN_REQUEST_USER_AGENT.put(userAgent.text)
        EgrnSettingsReader.EGRN_DISABLE_SSL_FOR_REQUEST.put(disableSSLforRequests.isSelected)

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
            EgrnSettingsReader.REQUEST_LIMIT.put(2)
        }

        try {
            var limit = Integer.valueOf(egrnRequestSelectionLimit.text)

            if (limit <= 10) {
                limit = 10
            }

            if (limit > 500) {
                limit = 500
            }

            EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.put(limit)
        } catch (e: NumberFormatException) {
            Logging.warn(e.message + "(need numeric)")
            EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.put(100)
        }

        try {
            var delay = Integer.valueOf(egrnRequestDelay.text)

            if (delay < 0) {
                delay = 0
            }

            EgrnSettingsReader.REQUEST_DELAY.put(delay)
        } catch (e: NumberFormatException) {
            Logging.warn(e.message + "(need numeric)")
            EgrnSettingsReader.REQUEST_DELAY.put(1)
        }
        //debug call
        RussiaAddressHelperPlugin.getUnprocessedEntities()
    }
}