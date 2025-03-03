package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.SettingsSaver.Companion.saveDouble
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.SettingsSaver.Companion.saveInteger
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.*

class EgrnRequestSettingsPanel : JPanel(GridBagLayout()) {
    private val egrnUrl = JosmTextField()
    private val nspdUrl = JosmTextField()
    private val userAgent = JosmTextField()
    private val egrnRequestLimit = JosmTextField(3)
    private val egrnRequestSelectionLimit = JosmTextField(3)
    private val egrnTotalRequestsCounter = JosmTextField(9)
    private val egrnRequestDelay = JosmTextField(3)
    private val disableSSLforRequests = JCheckBox(I18n.tr("Disable SSL for EGRN requests"))
    private val requestPixelResolution = JosmTextField(3)
    private val requestBoundaryMargin = JosmTextField(3)

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)
        panel.add(disableSSLforRequests, GBC.eol())

        panel.add(JLabel(I18n.tr("EGRN request url:")), GBC.std())
        panel.add(nspdUrl, GBC.eop().fill(GBC.HORIZONTAL).insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("EGRN request user agent string:")), GBC.std())
        panel.add(userAgent, GBC.eop().fill(GBC.HORIZONTAL).insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Request limit (from 1 to 10):")), GBC.std())
        panel.add(egrnRequestLimit, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Request limit for single select action:")), GBC.std())
        panel.add(egrnRequestSelectionLimit, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Delay between requests in seconds:")), GBC.std())
        panel.add(egrnRequestDelay, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Total requests to EGRN in current session (total/success):")), GBC.std())
        panel.add(egrnTotalRequestsCounter, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Resolution of request, pixel per meter:")), GBC.std())
        panel.add(requestPixelResolution, GBC.eop().insets(5, 0, 0, 5))

        panel.add(JLabel(I18n.tr("Boundary margin extension, meters:")), GBC.std())
        panel.add(requestBoundaryMargin, GBC.eop().insets(5, 0, 0, 5))

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Initializes the panel from preferences
     */
    fun initFromPreferences() {
        egrnUrl.text = EgrnSettingsReader.EGRN_URL_REQUEST.get()
        nspdUrl.text = EgrnSettingsReader.NSPD_GET_FEATURE_REQUEST_URL.get()
        userAgent.text = EgrnSettingsReader.EGRN_REQUEST_USER_AGENT.get()
        egrnRequestLimit.text = EgrnSettingsReader.REQUEST_LIMIT.get().toString()
        egrnRequestSelectionLimit.text = EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION.get().toString()
        egrnRequestDelay.text = EgrnSettingsReader.REQUEST_DELAY.get().toString()
        disableSSLforRequests.isSelected = EgrnSettingsReader.EGRN_DISABLE_SSL_FOR_REQUEST.get()
        egrnTotalRequestsCounter.text =
            "${RussiaAddressHelperPlugin.totalRequestsPerSession}/${RussiaAddressHelperPlugin.totalSuccessRequestsPerSession}"
        egrnTotalRequestsCounter.isEnabled = false
        requestPixelResolution.text = EgrnSettingsReader.REQUEST_PIXEL_RESOLUTION.get().toString()
        requestBoundaryMargin.text = EgrnSettingsReader.REQUEST_BOUNDS_MARGIN.get().toString()
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        EgrnSettingsReader.EGRN_URL_REQUEST.put(egrnUrl.text)
        EgrnSettingsReader.NSPD_GET_FEATURE_REQUEST_URL.put(nspdUrl.text)
        EgrnSettingsReader.EGRN_REQUEST_USER_AGENT.put(userAgent.text)
        EgrnSettingsReader.EGRN_DISABLE_SSL_FOR_REQUEST.put(disableSSLforRequests.isSelected)


        saveInteger(EgrnSettingsReader.REQUEST_LIMIT, egrnRequestLimit.text, 1, 10)
        saveInteger(EgrnSettingsReader.REQUEST_LIMIT_PER_SELECTION, egrnRequestSelectionLimit.text, 10, 500)
        saveInteger(EgrnSettingsReader.REQUEST_DELAY,egrnRequestDelay.text, 0, 30)
        saveInteger(EgrnSettingsReader.REQUEST_BOUNDS_MARGIN, requestBoundaryMargin.text, 0, 200)
        saveDouble(EgrnSettingsReader.REQUEST_PIXEL_RESOLUTION, requestPixelResolution.text, 1.0, 10.0)

        //debug call
        RussiaAddressHelperPlugin.cache.getUnprocessed()
    }

}