/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.data.adselection.datahandlers;

import com.android.adservices.ohttp.ObliviousHttpRequestContext;
import com.android.adservices.service.adselection.encryption.AdSelectionEncryptionKey;

import com.google.common.base.Preconditions;

import java.util.Objects;

/** Class to validate data handlers when they are used for persisting or reading from DB. */
public class DBValidator {

    /** Validate that the {@EncryptionContext} object can be writtent to DB. */
    public static void validateEncryptionContext(EncryptionContext encryptionContext) {
        ObliviousHttpRequestContext requestContext = encryptionContext.getOhttpRequestContext();
        Objects.requireNonNull(requestContext.keyConfig().serializeKeyConfigToBytes());
        Objects.requireNonNull(requestContext.encapsulatedSharedSecret().serializeToBytes());

        Preconditions.checkArgument(
                encryptionContext.getAdSelectionEncryptionKeyType()
                        != AdSelectionEncryptionKey.AdSelectionEncryptionKeyType.UNASSIGNED);
    }

    /** Validate that the {@ReportingData} object can be written to the DB. */
    public static void validateReportingData(ReportingData reportingData) {
        Objects.requireNonNull(reportingData.getBuyerWinReportingUri());
        Objects.requireNonNull(reportingData.getSellerWinReportingUri());
    }
}
