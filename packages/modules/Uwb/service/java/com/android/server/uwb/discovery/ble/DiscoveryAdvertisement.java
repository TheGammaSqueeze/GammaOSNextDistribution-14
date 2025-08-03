/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.uwb.discovery.ble;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.Log;
import android.util.SparseArray;

import com.android.server.uwb.discovery.info.FiraProfileSupportInfo;
import com.android.server.uwb.discovery.info.RegulatoryInfo;
import com.android.server.uwb.discovery.info.UwbIndicationData;
import com.android.server.uwb.discovery.info.VendorSpecificData;
import com.android.server.uwb.util.ArrayUtils;
import com.android.server.uwb.util.DataTypeConversionUtil;

import com.google.common.primitives.Bytes;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Holds data of the BLE discovery advertisement according to FiRa BLE OOB v1.0 specification.
 */
public class DiscoveryAdvertisement {
    private static final String LOG_TAG = DiscoveryAdvertisement.class.getSimpleName();

    // Mask and value of the FiRa specific field type field within each AD field.
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_MASK = (byte) 0xF0;
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_UWB_INDICATION_DATA = 0x1;
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_VENDOR_SPECIFIC_DATA = 0x2;
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_UWB_REGULATORY_INFO = 0x3;
    private static final byte FIRA_SPECIFIC_FIELD_TYPE_FIRA_PROFILE_SUPPORT_INFO = 0x4;

    // FiRa specific field length field within each AD field.
    private static final byte FIRA_SPECIFIC_FIELD_LENGTH_MASK = 0x0F;

    public final UwbIndicationData uwbIndicationData;
    public final RegulatoryInfo regulatoryInfo;
    public final FiraProfileSupportInfo firaProfileSupportInfo;
    public final VendorSpecificData[] vendorSpecificData;

    /**
     * Generate the DiscoveryAdvertisement from raw bytes arrays.
     *
     * @param serviceData byte array containing the UWB BLE Advertiser Service Data encoding based
     *     on the FiRa specification.
     * @param vendorSpecificDataArray maps UWB vendor ID to vendor specific encoded data.
     * @return decode bytes into {@link DiscoveryAdvertisement}, else null if invalid.
     */
    @Nullable
    public static DiscoveryAdvertisement fromBytes(
            @Nullable byte[] serviceData, @Nullable SparseArray<byte[]> vendorSpecificDataArray) {
        if (ArrayUtils.isEmpty(serviceData)) {
            logw("Failed to convert empty into BLE Discovery advertisement.");
            return null;
        }

        ByteBuffer byteBuffer = ByteBuffer.wrap(serviceData);

        UwbIndicationData uwbIndicationData = null;
        RegulatoryInfo regulatoryInfo = null;
        FiraProfileSupportInfo firaProfileSupportInfo = null;
        List<VendorSpecificData> vendorSpecificData = new ArrayList<>();

        while (byteBuffer.hasRemaining()) {
            // Parsing the next block of FiRa specific field based on given field type and length.
            byte firstByte = byteBuffer.get();
            byte fieldType = (byte) ((firstByte & FIRA_SPECIFIC_FIELD_TYPE_MASK) >> 4);
            byte fieldLength = (byte) (firstByte & FIRA_SPECIFIC_FIELD_LENGTH_MASK);
            if (byteBuffer.remaining() < fieldLength) {
                logw(
                        "Failed to convert bytes into BLE Discovery advertisement due to byte"
                                + " ended unexpectedly.");
                return null;
            }
            byte[] fieldBytes = new byte[fieldLength];
            byteBuffer.get(fieldBytes);

            if (fieldType == FIRA_SPECIFIC_FIELD_TYPE_UWB_INDICATION_DATA) {
                if (uwbIndicationData != null) {
                    logw(
                            "Failed to convert bytes into BLE Discovery advertisement due to"
                                    + " duplicate uwb indication data field.");
                    return null;
                }
                uwbIndicationData = UwbIndicationData.fromBytes(fieldBytes);
            } else if (fieldType == FIRA_SPECIFIC_FIELD_TYPE_UWB_REGULATORY_INFO) {
                if (regulatoryInfo != null) {
                    logw(
                            "Failed to convert bytes into BLE Discovery advertisement due to"
                                    + " duplicate regulatory info field.");
                    return null;
                }
                regulatoryInfo = RegulatoryInfo.fromBytes(fieldBytes);
            } else if (fieldType == FIRA_SPECIFIC_FIELD_TYPE_FIRA_PROFILE_SUPPORT_INFO) {
                if (firaProfileSupportInfo != null) {
                    logw(
                            "Failed to convert bytes into BLE Discovery advertisement due to"
                                    + " duplicate FiRa profile support info field.");
                    return null;
                }
                firaProfileSupportInfo = FiraProfileSupportInfo.fromBytes(fieldBytes);
            } else if (fieldType == FIRA_SPECIFIC_FIELD_TYPE_VENDOR_SPECIFIC_DATA) {
                // There can be multiple Vendor specific data fields.
                VendorSpecificData data =
                        VendorSpecificData.fromBytes(fieldBytes, Optional.empty());
                if (data != null) {
                    vendorSpecificData.add(data);
                }
            } else {
                logw(
                        "Failed to convert bytes into BLE Discovery advertisement due to invalid"
                                + " field type "
                                + fieldType);
                return null;
            }
        }

        // product/implementation specific data inside “Service Data” AD type object with CS UUID.
        // It should be used only if the GAP Advertiser role doesn’t support exposing “Manufacturer
        // Specific Data” AD type object.
        if (vendorSpecificDataArray != null && vendorSpecificDataArray.size() != 0) {
            if (!vendorSpecificData.isEmpty()) {
                logw(
                        "Failed to convert bytes into BLE Discovery advertisement due to Vendor"
                                + " Specific Data exist in both Service Data AD and Manufacturer"
                                + " Specific Data AD.");
                return null;
            }
            for (int i = 0; i < vendorSpecificDataArray.size(); i++) {
                vendorSpecificData.add(
                        VendorSpecificData.fromBytes(
                                vendorSpecificDataArray.valueAt(i),
                                Optional.of(vendorSpecificDataArray.keyAt(i))));
            }
        }

        return new DiscoveryAdvertisement(
                uwbIndicationData,
                regulatoryInfo,
                firaProfileSupportInfo,
                vendorSpecificData.toArray(new VendorSpecificData[0]));
    }

    /**
     * Generate raw bytes array from DiscoveryAdvertisement.
     *
     * @param adv the UWB BLE discovery Advertisement.
     * @param includeVendorSpecificData specify if the vendorSpecificData to be included in the
     *     advertisement bytes.
     * @return encoded bytes into byte array based on the FiRa specification.
     */
    public static byte[] toBytes(
            @NonNull DiscoveryAdvertisement adv, boolean includeVendorSpecificData) {
        byte[] data = new byte[] {};

        if (adv.uwbIndicationData != null) {
            data = Bytes.concat(data, convertUwbIndicationData(adv.uwbIndicationData));
        }
        if (adv.regulatoryInfo != null) {
            data = Bytes.concat(data, convertRegulatoryInfo(adv.regulatoryInfo));
        }
        if (adv.firaProfileSupportInfo != null) {
            data = Bytes.concat(data, convertFiraProfileSupportInfo(adv.firaProfileSupportInfo));
        }
        if (includeVendorSpecificData) {
            for (VendorSpecificData d : adv.vendorSpecificData) {
                data = Bytes.concat(data, convertVendorSpecificData(d));
            }
        }

        return data;
    }

    /**
     * Generate raw bytes array from DiscoveryAdvertisement.vendorSpecificData.
     *
     * @param adv the UWB BLE discovery Advertisement.
     * @return encoded Manufacturer Specific Data into byte array based on the FiRa specification.
     */
    public static byte[] getManufacturerSpecificDataInBytes(@NonNull DiscoveryAdvertisement adv) {
        if (adv.vendorSpecificData.length > 0) {
            return convertVendorSpecificData(adv.vendorSpecificData[0]);
        }
        return null;
    }

    private static byte convertByteLength(int size) {
        return DataTypeConversionUtil.i32ToByteArray(size)[3];
    }

    private static byte[] convertUwbIndicationData(UwbIndicationData uwbIndicationData) {
        byte[] data = UwbIndicationData.toBytes(uwbIndicationData);
        return Bytes.concat(
                new byte[] {
                    (byte)
                            (((FIRA_SPECIFIC_FIELD_TYPE_UWB_INDICATION_DATA << 4)
                                            & FIRA_SPECIFIC_FIELD_TYPE_MASK)
                                    | (convertByteLength(data.length)
                                            & FIRA_SPECIFIC_FIELD_LENGTH_MASK))
                },
                data);
    }

    private static byte[] convertRegulatoryInfo(RegulatoryInfo regulatoryInfo) {
        byte[] data = RegulatoryInfo.toBytes(regulatoryInfo);
        return Bytes.concat(
                new byte[] {
                    (byte)
                            (((FIRA_SPECIFIC_FIELD_TYPE_UWB_REGULATORY_INFO << 4)
                                            & FIRA_SPECIFIC_FIELD_TYPE_MASK)
                                    | (convertByteLength(data.length)
                                            & FIRA_SPECIFIC_FIELD_LENGTH_MASK))
                },
                data);
    }

    private static byte[] convertFiraProfileSupportInfo(
            FiraProfileSupportInfo firaProfileSupportInfo) {
        byte[] data = FiraProfileSupportInfo.toBytes(firaProfileSupportInfo);
        return Bytes.concat(
                new byte[] {
                    (byte)
                            (((FIRA_SPECIFIC_FIELD_TYPE_FIRA_PROFILE_SUPPORT_INFO << 4)
                                            & FIRA_SPECIFIC_FIELD_TYPE_MASK)
                                    | (convertByteLength(data.length)
                                            & FIRA_SPECIFIC_FIELD_LENGTH_MASK))
                },
                data);
    }

    private static byte[] convertVendorSpecificData(VendorSpecificData vendorSpecificData) {
        byte[] data = VendorSpecificData.toBytes(vendorSpecificData);
        return Bytes.concat(
                new byte[] {
                    (byte)
                            (((FIRA_SPECIFIC_FIELD_TYPE_VENDOR_SPECIFIC_DATA << 4)
                                            & FIRA_SPECIFIC_FIELD_TYPE_MASK)
                                    | (convertByteLength(data.length)
                                            & FIRA_SPECIFIC_FIELD_LENGTH_MASK))
                },
                data);
    }

    public DiscoveryAdvertisement(
            @Nullable UwbIndicationData uwbIndicationData,
            @Nullable RegulatoryInfo regulatoryInfo,
            @Nullable FiraProfileSupportInfo firaProfileSupportInfo,
            @Nullable VendorSpecificData[] vendorSpecificData) {
        this.uwbIndicationData = uwbIndicationData;
        this.regulatoryInfo = regulatoryInfo;
        this.firaProfileSupportInfo = firaProfileSupportInfo;
        this.vendorSpecificData = vendorSpecificData;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("DiscoveryAdvertisement: uwbIndicationData={")
                .append(uwbIndicationData)
                .append("} regulatoryInfo={")
                .append(regulatoryInfo)
                .append("} firaProfileSupportInfo={")
                .append(firaProfileSupportInfo)
                .append("} ")
                .append(Arrays.toString(vendorSpecificData));
        return sb.toString();
    }

    private static void logw(String log) {
        Log.w(LOG_TAG, log);
    }
}
