package org.openstreetmap.josm.plugins.dl.russiaaddresshelper.settings.io

import org.openstreetmap.josm.data.preferences.DoubleProperty
import org.openstreetmap.josm.data.preferences.IntegerProperty

open class SettingsSaver {
    companion object {
        fun saveInteger(property: IntegerProperty, value: String?, min: Int, max: Int) {
            if (value.isNullOrBlank()) return
            var integerValue = value.toIntOrNull()
            if (integerValue == null) {
                integerValue = property.defaultValue
            } else {
                if (integerValue < min) {
                    integerValue = min
                } else
                    if (integerValue > max) {
                        integerValue = max
                    }
            }
            property.put(integerValue)
        }

        fun saveDouble(doubleProperty: DoubleProperty, value: String?, min: Double, max: Double) {
            if (value.isNullOrBlank()) return
            var doubleValue = value.toDoubleOrNull()
            if (doubleValue == null) {
                doubleValue = doubleProperty.defaultValue
            } else {
                if (doubleValue.compareTo(min) <= 0) {
                    doubleValue = min
                } else
                    if (doubleValue.compareTo(max) > 0) {
                        doubleValue = max
                    }
            }
            doubleProperty.put(doubleValue)
        }
    }
}