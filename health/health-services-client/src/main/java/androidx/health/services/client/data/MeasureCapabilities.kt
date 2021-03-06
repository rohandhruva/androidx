/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.health.services.client.data

import android.os.Parcel
import android.os.Parcelable

/**
 * A place holder class that represents the capabilities of the
 * [androidx.health.services.client.MeasureClient] on the device.
 */
public data class MeasureCapabilities(
    /**
     * Set of supported [DataType] s for measure capture on this device.
     *
     * Some data types are not available for measurement; this is typically used to measure health
     * data (e.g. HR).
     */
    val supportedDataTypesMeasure: Set<DataType>,
) : Parcelable {
    override fun describeContents(): Int = 0

    override fun writeToParcel(dest: Parcel, flags: Int) {
        dest.writeTypedList(supportedDataTypesMeasure.toList())
    }

    public companion object {
        @JvmField
        public val CREATOR: Parcelable.Creator<MeasureCapabilities> =
            object : Parcelable.Creator<MeasureCapabilities> {
                override fun createFromParcel(source: Parcel): MeasureCapabilities? {
                    val measureDataTypes = ArrayList<DataType>()
                    source.readTypedList(measureDataTypes, DataType.CREATOR)
                    return MeasureCapabilities(
                        measureDataTypes.toSet(),
                    )
                }

                override fun newArray(size: Int): Array<MeasureCapabilities?> {
                    return arrayOfNulls(size)
                }
            }
    }
}
