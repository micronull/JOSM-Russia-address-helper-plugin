package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings

import org.openstreetmap.josm.gui.ExtendedDialog
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.TagSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.table.BuildingTagsTable
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.ImageProvider
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes
import java.awt.Color
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.*


class BuildingTagsSettingsPanel : JPanel(GridBagLayout()) {

    private val buildingTagsTable = BuildingTagsTable(TagSettingsReader.EGRN_BUILDING_TYPES_SETTINGS.get())
    private val upButton = JButton(ImageProvider.get("dialogs/up", ImageSizes.LARGEICON))
    private val downButton = JButton(ImageProvider.get("dialogs/down", ImageSizes.LARGEICON))
    private val removeButton = JButton(ImageProvider.get("dialogs/delete", ImageSizes.LARGEICON))
    private val resetButton = JButton(ImageProvider.get("dialogs/refresh", ImageSizes.LARGEICON))

    init {
        val panel: JPanel = this
        panel.border = BorderFactory.createEmptyBorder(5, 5, 5, 5)

        val infoLabel = JMultilineLabel(
            "Тип здания выводится из данных ЕГРН согласно этой таблице." +
                    "<br>Значения, полученные в полях <b>properties.descr</b> и <b>properties.options.purpose</b>" +
                    "<br> проверяются на вхождение подстрок из столбца Подстроки ЕГРН." +
                    "<br>Индивидуальные подстроки разделяются запятыми." +
                    "<br>Порядок проверки определяется порядком строк таблицы." +
                    "<br>Первое совпадение определяет результат. " +
                    "<br>При отсутствии совпадения присваивается <b>building=yes<b>",
            false,
            true)
        infoLabel.setMaxWidth(600)
        panel.add(infoLabel, GBC.eop().anchor(GBC.NORTH).fill(GBC.HORIZONTAL))

        buildingTagsTable.tableHeader.reorderingAllowed = false
        buildingTagsTable.selectionBackground = Color.BLUE
        buildingTagsTable.columnSelectionAllowed = false
        buildingTagsTable.cellSelectionEnabled = false
        buildingTagsTable.rowSelectionAllowed = true
        buildingTagsTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        buildingTagsTable.preferredScrollableViewportSize = Dimension(500, 200)
        buildingTagsTable.columnModel.getColumn(0).preferredWidth = 100
        buildingTagsTable.columnModel.getColumn(1).preferredWidth = 400
        val tablePanel = JPanel(GridBagLayout())
        tablePanel.add(JScrollPane(buildingTagsTable), GBC.std().anchor(GBC.NORTH).insets(10, 0, 10, 0))

        upButton.addActionListener {
            run {
                buildingTagsTable.upPressed()
            }
        }
        downButton.addActionListener {
            run {
                buildingTagsTable.downPressed()
            }
        }

        removeButton.addActionListener {
            run {
                buildingTagsTable.removePressed()
            }
        }

        val buttonPane = JPanel(GridBagLayout())
        buttonPane.add(upButton, GBC.eol())

        buttonPane.add(downButton, GBC.eol().insets(0, 0, 0, 50))
        buttonPane.add(removeButton, GBC.eol())
        buttonPane.add(resetButton, GBC.eol())
        tablePanel.add(buttonPane)


        upButton.toolTipText = I18n.tr("Move up")
        downButton.toolTipText = I18n.tr("Move down")
        removeButton.toolTipText = I18n.tr("Remove row")
        resetButton.toolTipText = I18n.tr("Reset to defaults")

        panel.add(tablePanel, GBC.std().anchor(GBC.NORTH))

        val buttonTexts = arrayOf(
            I18n.tr("Yes"),
            I18n.tr("No"),
        )
        val dialog = ExtendedDialog(
            panel,
            I18n.tr("Reset to defaults?"),
            *buttonTexts
        )
        dialog.size = Dimension(300, 100)
        dialog.setContent(JLabel(I18n.tr("Reset building tag settings to defaults?")), false)
        dialog.setButtonIcons("dialogs/refresh", "cancel")

        resetButton.addActionListener {
            run {
                dialog.showDialog()
                val answer = dialog.value
                if (answer == 1) buildingTagsTable.fillData(TagSettingsReader.EGRN_BUILDING_TYPES_SETTINGS.defaultValue)
            }
        }
    }

    /**
     * Saves the current values to the preferences
     */
    fun saveToPreferences() {
        TagSettingsReader.EGRN_BUILDING_TYPES_SETTINGS.put(buildingTagsTable.getData())
    }

}