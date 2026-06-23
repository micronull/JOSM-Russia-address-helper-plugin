package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader.Companion.ADDRESS_STOP_WORDS
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.table.StopWordsTable
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.ImageProvider
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.*

class StopWordsTablePanel : JPanel(GridBagLayout()) {
    private val label =
        JLabel(I18n.tr("Addresses containing words from this table will trigger Adress is not recognized error"))
    private val stopWordsTable: StopWordsTable = StopWordsTable(listOf<String>())
    private val updateFromDefaultsButton = JButton(ImageProvider.get("dialogs/refresh", ImageSizes.LARGEICON))

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        panel.add(label, GBC.eol().insets(0, 0, 0, 0))

        stopWordsTable.tableHeader.reorderingAllowed = false

        stopWordsTable.rowSelectionAllowed = false
        stopWordsTable.columnSelectionAllowed = false
        stopWordsTable.cellSelectionEnabled = false
        stopWordsTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        stopWordsTable.preferredScrollableViewportSize = Dimension(205, 300)
        stopWordsTable.columnModel.getColumn(0).preferredWidth = 200

        panel.add(JScrollPane(stopWordsTable), GBC.std().insets(0, 0, 0, 0))
        panel.add(updateFromDefaultsButton, GBC.eol())

        updateFromDefaultsButton.toolTipText = I18n.tr("Update from defaults")

        fillTableWithData()

        panel.add(Box.createVerticalGlue(), GBC.eol().fill())

        updateFromDefaultsButton.addActionListener {
            run {
                updateFromDefaults()
                fillTableWithData()
            }
        }

    }

    /**
     * Saves the current values to the preferences
     */

    private fun fillTableWithData() {
        stopWordsTable.fillData(ADDRESS_STOP_WORDS.get())
    }

    fun saveToPreferences() {
        ADDRESS_STOP_WORDS.put(stopWordsTable.getData())
    }

    fun updateFromDefaults() {
        val existingData = ADDRESS_STOP_WORDS.get().toMutableSet()
        existingData.addAll(ADDRESS_STOP_WORDS.defaultValue)
        ADDRESS_STOP_WORDS.put(existingData.toMutableList())
    }

    fun isDefaultsHaveNewValues(): Boolean {
        return !ADDRESS_STOP_WORDS.get().containsAll(ADDRESS_STOP_WORDS.defaultValue)
    }
}