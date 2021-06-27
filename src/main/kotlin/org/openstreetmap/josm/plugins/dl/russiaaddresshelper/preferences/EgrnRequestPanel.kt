package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.preferences

import org.openstreetmap.josm.gui.widgets.JosmTextField
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.io.EgrnReader
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import java.awt.GridBagLayout
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.JLabel
import javax.swing.JPanel

class EgrnRequestPanel : JPanel(GridBagLayout()) {
    private val egrnUrl = JosmTextField();
    private val egrnRequestLimit = JosmTextField(4);

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        panel.add(JLabel(I18n.tr("EGRN request url:")), GBC.std().insets(5, 5, 5, 5))
        panel.add(egrnUrl, GBC.eop().fill(GBC.HORIZONTAL))

        panel.add(JLabel(I18n.tr("Request limit:")), GBC.std().insets(5, 5, 5, 5))
        panel.add(egrnRequestLimit, GBC.eop())

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())
    }

    /**
     * Initializes the panel from preferences
     */
    fun initFromPreferences() {
        egrnUrl.text = EgrnReader.EGRN_URL_REQUEST.get()
        egrnRequestLimit.text = EgrnReader.REQUEST_LIMIT.get().toString()
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences(){
        EgrnReader.EGRN_URL_REQUEST.put(egrnUrl.text)
        EgrnReader.REQUEST_LIMIT.parseAndPut(egrnRequestLimit.text)
    }
}