package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.gui

import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.gui.ExtendedDialog
import org.openstreetmap.josm.gui.widgets.JMultilineLabel
import org.openstreetmap.josm.tools.GBC
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.ImageProvider
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagLayout
import javax.swing.*

class TagConflictSimpleDialog(
    parent: Component?,
    title: String?,
    p: OsmPrimitive,
    conflictedTags: Map<String, String>,
    egrnAddress: String?,
    vararg buttonTexts: String?
) :
    ExtendedDialog(parent, title, *buttonTexts) {
    private val tagsTable: TagConflictsTable = TagConflictsTable(p.keys, conflictedTags)
    private val takeAllLeftButton = JButton(ImageProvider.get("dialogs/last", ImageProvider.ImageSizes.LARGEICON))
    private val takeLeftButton = JButton(ImageProvider.get("dialogs/next", ImageProvider.ImageSizes.LARGEICON))
    private val takeAllRightButton = JButton(ImageProvider.get("dialogs/first", ImageProvider.ImageSizes.LARGEICON))
    private val takeRightButton = JButton(ImageProvider.get("dialogs/previous", ImageProvider.ImageSizes.LARGEICON))
    private val clearButton = JButton(ImageProvider.get("dialogs/refresh", ImageProvider.ImageSizes.LARGEICON))

    init {

        val contentPanel = JPanel(GridBagLayout())
        val infoLabel = JMultilineLabel(
            "Запрос в ЕГРН вернул данные, которые противоречат уже существующим в ОСМ." +
                    "<br>Перепроверьте при возможности верность данных, и объедините конфликт." +
                    "<br>ИЛИ проигнорируйте данную проблему." +
                    "<br><b>Не вносите в ОСМ данные основанные на интерполяции!" +
                    "<br>Не вносите в ОСМ данные, взятые из неразрешенных источников " +
                    "<br>(другие карты, панорамы, сайты, которые ЯВНО не дали разрешение на использование)<br>" +
                    "<br>ЕГРН не является эталонным, безошибочным источником данных!</b>",
            false,
            true
        )
        infoLabel.setMaxWidth(800)
        contentPanel.add(infoLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL).insets(0, 10, 0, 10))
        val addressLabel = JLabel("Адрес из ЕГРН: $egrnAddress")
        contentPanel.add(addressLabel, GBC.eop().anchor(GBC.CENTER).fill(GBC.HORIZONTAL).insets(0, 10, 0, 10))
        val tablePanel = JPanel(GridBagLayout())
        tagsTable.tableHeader.reorderingAllowed = false
        tagsTable.selectionForeground = Color.BLACK
        tagsTable.columnSelectionAllowed = false
        tagsTable.cellSelectionEnabled = false
        tagsTable.rowSelectionAllowed = true
        tagsTable.autoResizeMode = JTable.AUTO_RESIZE_OFF
        tagsTable.preferredScrollableViewportSize = Dimension(800, 400)
        tagsTable.columnModel.getColumn(0).preferredWidth = 150
        tagsTable.columnModel.getColumn(1).preferredWidth = 100
        tagsTable.columnModel.getColumn(2).preferredWidth = 100
        tagsTable.columnModel.getColumn(3).preferredWidth = 100
        val pane = JScrollPane(tagsTable)
        pane.preferredSize = Dimension(455, 100)
        tablePanel.add(pane, GBC.eol().anchor(GBC.NORTH).fill(GBC.HORIZONTAL))

        //add buttons
        val buttonPanel = JPanel(GridBagLayout())
        buttonPanel.add(takeAllLeftButton, GBC.std())
        buttonPanel.add(takeLeftButton, GBC.std().insets(10, 0, 50, 0))
        buttonPanel.add(clearButton, GBC.std().insets(10, 0, 50, 0))
        buttonPanel.add(takeRightButton, GBC.std().insets(0, 0, 10, 0))
        buttonPanel.add(takeAllRightButton, GBC.eol())
        tablePanel.add(buttonPanel, GBC.std().insets(0, 10, 0, 20).anchor(GBC.CENTER))
        contentPanel.add(tablePanel)

        configureTableButtons()

        this.setContent(contentPanel)
        this.setButtonIcons("dialogs/conflict", "dialogs/edit", "cancel")
        this.setupDialog()
        tagsTable.setButton(this.buttons.first())
        tagsTable.mergeCompleteCheck()
    }

    private fun configureTableButtons() {
        takeAllLeftButton.addActionListener {
            run {
                tagsTable.takeAllLeft()
            }
        }

        takeLeftButton.addActionListener {
            run {
                tagsTable.takeLeft()
            }
        }

        takeAllRightButton.addActionListener {
            run {
                tagsTable.takeAllRight()
            }
        }

        takeRightButton.addActionListener {
            run {
                tagsTable.takeRight()
            }
        }

        clearButton.addActionListener {
            run {
                tagsTable.clearMerged()
            }
        }

        takeAllLeftButton.toolTipText = I18n.tr("Take all existing")
        takeLeftButton.toolTipText = I18n.tr("Take selected existing")
        takeAllRightButton.toolTipText = I18n.tr("Take all loaded")
        takeRightButton.toolTipText = I18n.tr("Take selected loaded")
        clearButton.toolTipText = I18n.tr("Clear decisions")
    }

    fun getTagsData(): Map<String, String> {
        return tagsTable.getData()
    }

}