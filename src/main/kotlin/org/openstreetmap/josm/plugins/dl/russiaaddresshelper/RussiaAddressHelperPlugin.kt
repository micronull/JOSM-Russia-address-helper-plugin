package org.openstreetmap.josm.plugins.dl.russiaaddresshelper

import org.openstreetmap.josm.actions.UploadAction
import org.openstreetmap.josm.data.Version
import org.openstreetmap.josm.data.coor.EastNorth
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.validation.OsmValidator
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.gui.MapFrame
import org.openstreetmap.josm.gui.Notification
import org.openstreetmap.josm.gui.preferences.PreferenceSetting
import org.openstreetmap.josm.plugins.Plugin
import org.openstreetmap.josm.plugins.PluginInformation
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions.ClickAction
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.actions.SelectAction
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EGRNResponse
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.EgrnApi
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.ParsedAddressInfo
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.PluginSetting
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io.EgrnSettingsReader
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks.EGRNCleanPluginCache
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.uploadhooks.EGRNUploadTagFilter
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.*
import org.openstreetmap.josm.tools.I18n
import org.openstreetmap.josm.tools.ImageProvider
import javax.swing.JMenu
import javax.swing.JOptionPane

class RussiaAddressHelperPlugin(info: PluginInformation) : Plugin(info) {
    init {
        menuInit(MainApplication.getMenu().dataMenu)

        versionInfo = info.version
    }

    companion object {
        val ACTION_NAME = I18n.tr("Russia address helper")!!
        val ICON_NAME = "icon.svg"

        lateinit var versionInfo: String

        var egrnResponses: Map<OsmPrimitive, Triple<EastNorth?, EGRNResponse, ParsedAddressInfo>> = mutableMapOf()
        var ignoredValidators: Map<OsmPrimitive, MutableSet<EGRNTestCode>> = mutableMapOf()

        var egrnUploadTagFilter : EGRNUploadTagFilter = EGRNUploadTagFilter()
        var cleanPluginCache : EGRNCleanPluginCache = EGRNCleanPluginCache()
        //debug tool, to get any buildings which were processed by plugin but not validated
        var processedByValidators: Map<OsmPrimitive, MutableSet<EGRNTestCode>> = mutableMapOf()

        val selectAction: SelectAction = SelectAction()
        val clickAction: ClickAction = ClickAction()

        fun getEgrnClient(): EgrnApi {
            val userAgent = String.format(
                EgrnSettingsReader.EGRN_REQUEST_USER_AGENT.get(),
                Version.getInstance().versionString,
                versionInfo
            )

            return EgrnApi(EgrnSettingsReader.EGRN_URL_REQUEST.get(), userAgent)
        }

        fun ignoreValidator(primitive: OsmPrimitive, code: EGRNTestCode) {
            val ignoredValidators = RussiaAddressHelperPlugin.ignoredValidators[primitive]
            if (ignoredValidators == null || ignoredValidators.isEmpty()) {
                RussiaAddressHelperPlugin.ignoredValidators =
                    RussiaAddressHelperPlugin.ignoredValidators.plus(Pair(primitive, mutableSetOf(code)))
            } else {
                ignoredValidators.add(code)
                RussiaAddressHelperPlugin.ignoredValidators =
                    RussiaAddressHelperPlugin.ignoredValidators.plus(Pair(primitive, ignoredValidators))
            }
        }

        fun isIgnored(primitive: OsmPrimitive, code: EGRNTestCode): Boolean {
            val ignoredValidators = RussiaAddressHelperPlugin.ignoredValidators[primitive]
            return ignoredValidators != null && ignoredValidators.contains(code)
        }

        fun markAsProcessed(primitive: OsmPrimitive, code: EGRNTestCode) {
            val processedByValidators = RussiaAddressHelperPlugin.processedByValidators[primitive]
            if (processedByValidators == null || processedByValidators.isEmpty()) {
                RussiaAddressHelperPlugin.processedByValidators =
                    RussiaAddressHelperPlugin.processedByValidators.plus(Pair(primitive, mutableSetOf(code)))
            } else {
                processedByValidators.add(code)
                RussiaAddressHelperPlugin.processedByValidators =
                    RussiaAddressHelperPlugin.processedByValidators.plus(Pair(primitive, processedByValidators))
            }
        }

        fun removeFromAllCaches (primitive: OsmPrimitive) {
            if (egrnResponses[primitive]!=null) {
                egrnResponses = egrnResponses.minus(primitive)
            }
            if (ignoredValidators[primitive] != null) {
                ignoredValidators = ignoredValidators.minus(primitive)
            }
            if (processedByValidators[primitive]!=null) {
                processedByValidators = processedByValidators.minus(primitive)
            }
        }

        fun getUnprocessedEntities() {
            val unprocessed = egrnResponses.keys.minus(processedByValidators.keys).minus(ignoredValidators.keys)
            if (unprocessed.isNotEmpty()) {
                val egrnResponses = egrnResponses.filter { unprocessed.contains(it.key) }
                Notification("Не обработано ни одним валидатором ${unprocessed.size} обьектов").setIcon(JOptionPane.WARNING_MESSAGE)
                    .show()
            }
        }
    }

    override fun getPreferenceSetting(): PreferenceSetting {
        return PluginSetting()
    }

    override fun mapFrameInitialized(oldFrame: MapFrame?, newFrame: MapFrame?) {

        OsmValidator.addTest(EGRNEmptyResponseTest::class.java)
        OsmValidator.addTest(EGRNFuzzyStreetMatchingTest::class.java)
        OsmValidator.addTest(EGRNInitialsStreetMatchingTest::class.java)
        OsmValidator.addTest(EGRNMultipleValidAddressTest::class.java)
        OsmValidator.addTest(EGRNStreetNotFoundTest::class.java)
        OsmValidator.addTest(EGRNAddressAddedTest::class.java)
        OsmValidator.addTest(EGRNCantParseAddressTest::class.java)
        OsmValidator.addTest(EGRNFlatsInAddressTest::class.java)
        OsmValidator.addTest(EGRNPlaceNotFoundTest::class.java)
        OsmValidator.addTest(EGRNFuzzyOrInitialsPlaceMatchTest::class.java)
        OsmValidator.addTest(EGRNDuplicateAddressesTest::class.java)

        UploadAction.registerUploadHook(cleanPluginCache, true);
        UploadAction.registerUploadHook(egrnUploadTagFilter, true);

    }

    private fun menuInit(menu: JMenu) {
        menu.isVisible = true

        if (menu.itemCount > 0) {
            menu.addSeparator()
        }

        val subMenu = JMenu(ACTION_NAME)
        subMenu.icon =
            ImageProvider(ICON_NAME).resource.getPaddedIcon(ImageProvider.ImageSizes.SMALLICON.imageDimension)

        subMenu.add(selectAction)
        subMenu.add(clickAction)

        menu.add(subMenu)
    }

}