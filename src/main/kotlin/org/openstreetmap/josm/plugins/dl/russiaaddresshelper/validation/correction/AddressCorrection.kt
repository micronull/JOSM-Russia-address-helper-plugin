package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.validation.correction

import org.openstreetmap.josm.data.correction.Correction
import org.openstreetmap.josm.plugins.dl.russiaaddresshelper.parsers.ParsedAddress

class AddressCorrection(val address: ParsedAddress, val hasDouble: Boolean) : Correction