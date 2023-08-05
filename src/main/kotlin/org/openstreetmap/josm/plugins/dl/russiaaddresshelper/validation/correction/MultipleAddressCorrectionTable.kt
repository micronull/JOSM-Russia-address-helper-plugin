package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.correction

import org.openstreetmap.josm.gui.correction.CorrectionTable

class MultipleAddressCorrectionTable(corrections: MutableList<AddressCorrection>) :
    CorrectionTable<AddressCorrectionTableModel>(AddressCorrectionTableModel(corrections))