package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation

import org.openstreetmap.josm.command.Command
import org.openstreetmap.josm.data.osm.OsmPrimitive
import org.openstreetmap.josm.data.osm.Relation
import org.openstreetmap.josm.data.osm.Way
import org.openstreetmap.josm.data.validation.Severity
import org.openstreetmap.josm.data.validation.Test
import org.openstreetmap.josm.data.validation.TestError
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.RussiaAddressHelperPlugin
import org.openstreetmap.josm.tools.I18n

class EGRNEmptyResponseTest : Test(
    I18n.tr("EGRN empty response"),
    I18n.tr("EGRN test for empty response from registry")
) {

    override fun visit(w: Way) {
        visitForPrimitive(w)
    }

    override fun visit(r: Relation) {
        visitForPrimitive(r)
    }

    fun visitForPrimitive(p: OsmPrimitive) {
        if (!p.isUsable) return
        val response = RussiaAddressHelperPlugin.cache.get(p)?.data
        if (response == null || response.responses.isEmpty()) {
            if (RussiaAddressHelperPlugin.cache.contains(p)) {
                RussiaAddressHelperPlugin.cache.markProcessed(p, EGRNTestCode.EGRN_RETURNED_EMPTY_RESPONSE)
                errors.add(
                    TestError.builder(this, Severity.OTHER, EGRNTestCode.EGRN_RETURNED_EMPTY_RESPONSE.code)
                        .message(I18n.tr( EGRNTestCode.EGRN_RETURNED_EMPTY_RESPONSE.message))
                        .primitives(p)
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
            if (w != null) {
                RussiaAddressHelperPlugin.cache.ignoreValidator(w, EGRNTestCode.getByCode(testError.code)!!)
            }
        }
        return null
    }

    override fun isFixable(testError: TestError): Boolean {
        return false
    }
}