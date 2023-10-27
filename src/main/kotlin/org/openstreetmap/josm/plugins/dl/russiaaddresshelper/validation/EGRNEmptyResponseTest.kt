package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.tools.I18n

class EGRNEmptyResponseTest : Test(
    I18n.tr("EGRN empty response"),
    I18n.tr("EGRN test for empty response from registry")
) {
    override fun visit(w: Way) {
        if (!w.isUsable) return
        val response = RussiaAddressHelperPlugin.egrnResponses[w]?.second
        if (response == null || response.results.isEmpty()) {
            if (RussiaAddressHelperPlugin.egrnResponses[w] != null) {
                RussiaAddressHelperPlugin.markAsProcessed(w, EGRNTestCode.EGRN_RETURNED_EMPTY_RESPONSE)
                errors.add(
                    TestError.builder(this, Severity.OTHER, EGRNTestCode.EGRN_RETURNED_EMPTY_RESPONSE.code)
                        .message(I18n.tr("EGRN returned empty response"))
                        .primitives(w)
                        .build()
                )
            }
        }
    }

    override fun fixError(testError: TestError): Command? {
        // primitives list can be empty if all primitives have been purged
        val it: Iterator<OsmPrimitive?> = testError.primitives.iterator()
        if (it.hasNext()) {
            val w = it.next()
            if (w!= null) {
                RussiaAddressHelperPlugin.ignoreValidator(w, EGRNTestCode.getByCode(testError.code)!!)
            }
        }
        return null
    }

    override fun isFixable(testError: TestError): Boolean {
        return false
    }
}