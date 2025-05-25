package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.ChangePropertyCommand
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.Relation
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.gui.MainApplication
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.api.NSPDLayer
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.tools.TagHelper
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.gui.TagConflictSimpleDialog
import org.openstreetmap.josm.tools.I18n


class EGRNConflictedDataTest : Test(
    I18n.tr("EGRN data conflicts with OSM data"),
    I18n.tr("EGRN data conflicts with existing OSM data")
) {

    override fun visit(w: Way) {
        visitForPrimitive(w)
    }

    override fun visit(r: Relation) {
        visitForPrimitive(r)
    }

    private fun visitForPrimitive(p: OsmPrimitive) {
        if (!p.isUsable) return
        val egrnResult = RussiaAddressHelperPlugin.cache.get(p)
        if (egrnResult != null && !RussiaAddressHelperPlugin.cache.isIgnored(p, EGRNTestCode.EGRN_CONFLICTED_DATA)) {
            val conflictedTags = getConflictedTags(egrnResult, p)

            if (conflictedTags.isNotEmpty()) {
                RussiaAddressHelperPlugin.cache.markProcessed(p, EGRNTestCode.EGRN_CONFLICTED_DATA)
                errors.add(
                    TestError.builder(
                        this, Severity.WARNING,
                        EGRNTestCode.EGRN_CONFLICTED_DATA.code
                    )
                        .message(
                            I18n.tr(EGRNTestCode.EGRN_CONFLICTED_DATA.message),
                            conflictedTags.keys.joinToString(", ")
                        )
                        .primitives(p)
                        .build()
                )
            }
        }

    }

    private fun getConflictedTags(
        egrnResult: ValidationRecord,
        p: OsmPrimitive
    ): Map<String, String> {
        val preferredAddress = egrnResult.addressInfo?.getPreferredAddress()
        val tagsFromEgrn = mutableMapOf<String, String>()

        if (preferredAddress != null) {
            tagsFromEgrn.putAll(preferredAddress.getOsmAddress().getBaseAddressTagsWithSource())
        }
        var buildingFeature = egrnResult.data.responses[NSPDLayer.BUILDING]?.features?.firstOrNull()
        if (buildingFeature != null) {
            tagsFromEgrn.putAll(TagHelper.getBuildingTags(buildingFeature, NSPDLayer.BUILDING))
        } else {
            buildingFeature = egrnResult.data.responses[NSPDLayer.UNFINISHED]?.features?.firstOrNull()
            if (buildingFeature != null) {
                tagsFromEgrn.putAll(TagHelper.getBuildingTags(buildingFeature, NSPDLayer.UNFINISHED))
            }
        }
        return tagsFromEgrn.filter { p.hasTag(it.key) && p[it.key] != it.value && !(it.key == "building" && it.value == "yes") }
    }

    override fun fixError(testError: TestError): Command? {
        val primitive = testError.primitives.iterator().next()

        val buttonTexts = arrayOf(
            I18n.tr("Add merged data"),
            I18n.tr("Ignore"),
            I18n.tr("Cancel")
        )
        val dialog = TagConflictSimpleDialog(
            MainApplication.getMainFrame(),
            I18n.tr("Валидация конфликтных данных"),
            primitive,
            conflictedTags = getConflictedTags(RussiaAddressHelperPlugin.cache.get(primitive)!!, primitive),
            egrnAddress = RussiaAddressHelperPlugin.cache.get(primitive)!!.addressInfo?.getPreferredAddress()?.egrnAddress,
            *buttonTexts
        )

        dialog.showDialog()

        val answer = dialog.value

        if (answer == 1) {
            RussiaAddressHelperPlugin.cache.ignoreValidator(primitive, EGRNTestCode.EGRN_CONFLICTED_DATA)
            return ChangePropertyCommand(listOf(primitive), dialog.getTagsData())
        }

        if (answer == 2) {
            RussiaAddressHelperPlugin.cache.ignoreValidator(primitive, EGRNTestCode.EGRN_CONFLICTED_DATA)
        }

        if (answer == 3) {
            return null
        }

        return null
    }

    override fun isFixable(testError: TestError): Boolean {
        return testError.tester is EGRNConflictedDataTest
    }

}