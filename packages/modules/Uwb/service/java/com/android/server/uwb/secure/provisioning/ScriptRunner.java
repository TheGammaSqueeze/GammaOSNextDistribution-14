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

package com.android.server.uwb.secure.provisioning;

import android.annotation.NonNull;
import android.util.Log;

import com.android.server.uwb.secure.SecureElementChannel;
import com.android.server.uwb.secure.csml.CsmlUtil;
import com.android.server.uwb.secure.iso7816.CommandApdu;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.secure.iso7816.TlvDatum;
import com.android.server.uwb.secure.iso7816.TlvParser;
import com.android.server.uwb.util.ObjectIdentifier;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

class ScriptRunner {
    private static final String LOG_TAG = "ScriptExecutor";
    private static final TlvDatum.Tag SECURE_BLOB_TAG = new TlvDatum.Tag((byte) 0xDF, (byte) 0x51);
    private static final byte LAST_MANAGE_ADF_INDICATOR_P1 = (byte) 0x00;

    private SecureElementChannel mSecureElementChannel;

    ScriptRunner(@NonNull SecureElementChannel secureElementChannel) {
        mSecureElementChannel = secureElementChannel;
    }

    void run(@NonNull ScriptParser.ScriptContent scriptContent,
            @NonNull UUID serviceInstanceId,
            ProvisioningManager.ProvisioningCallback provisioningCallback)
            throws ProvisioningException {
        List<byte[]> primitiveApdus = scriptContent.mProvisioningApdus;
        if (!mSecureElementChannel.openChannel()) {
            throw new ProvisioningException("open logical channel error.");
        }
        for (byte[] apduBytes : primitiveApdus) {
            try {
                CommandApdu commandApdu = CommandApdu.parse(apduBytes);
                ApduCategory apduCategory = getApduCategory(commandApdu);
                if (apduCategory == ApduCategory.NOT_ALLOWED) {
                    throw new ProvisioningException("not allowed Command APDU."
                            + commandApdu.getIns());
                }
                ResponseApdu responseApdu = mSecureElementChannel.transmit(commandApdu);
                if (responseApdu.getStatusWord() != StatusWord.SW_NO_ERROR.toInt()) {
                    throw new ProvisioningException("cannot handle the provisioning apdu.");
                }
                logd("apdu category: " + apduCategory);
                if (apduCategory == ApduCategory.GENERAL) {
                    // no further processing
                    continue;
                }

                Map<TlvDatum.Tag, List<TlvDatum>> tlvsMap =
                        TlvParser.parseTlvs(responseApdu);
                Optional<ObjectIdentifier> adfOid = scriptContent.mAdfOid;
                if (adfOid.isEmpty()) {
                    List<TlvDatum> oids =  tlvsMap.get(CsmlUtil.OID_TAG);
                    if (oids != null && oids.size() > 0) {
                        adfOid = Optional.of(ObjectIdentifier.fromBytes(oids.get(0).value));
                    } else if (apduCategory != ApduCategory.MANAGE_ADF
                            && commandApdu.getP1() == LAST_MANAGE_ADF_INDICATOR_P1) {
                        throw new ProvisioningException(
                                "ADF OID must be provided in script or response.");
                    }
                }
                switch(apduCategory) {
                    case CREATE_ADF:
                        provisioningCallback.onAdfCreated(serviceInstanceId, adfOid.get());
                        break;
                    case MANAGE_ADF:
                        if (commandApdu.getP1() == LAST_MANAGE_ADF_INDICATOR_P1) {
                            provisioningCallback.onAdfProvisioned(serviceInstanceId, adfOid.get());
                        }
                        break;
                    case IMPORT_ADF:
                        List<TlvDatum>  secureBlobs = tlvsMap.get(SECURE_BLOB_TAG);
                        if (secureBlobs == null || secureBlobs.size() == 0) {
                            throw new ProvisioningException("SecureBlob is not available.");
                        }
                        provisioningCallback.onAdfImported(serviceInstanceId, adfOid.get(),
                                secureBlobs.get(0).value);
                        break;
                    case DELETE_ADF:
                        provisioningCallback.onAdfDeleted(serviceInstanceId, adfOid.get());
                        break;
                    default:
                        break;
                }
            } catch (IOException e) {
                throw new ProvisioningException(e);
            }
        }
    }

    private ApduCategory getApduCategory(CommandApdu commandApdu) {
        if (((commandApdu.getCla() & 0x84) == 0x84) || ((commandApdu.getCla() & 0xE0) == 0xE0)) {
            if (commandApdu.getIns() == (byte) 0xE0) {
                return ApduCategory.CREATE_ADF;
            } else if (commandApdu.getIns() == (byte) 0xEA) {
                return ApduCategory.MANAGE_ADF;
            } else if (commandApdu.getIns() == (byte) 0xEB) {
                return ApduCategory.IMPORT_ADF;
            } else if (commandApdu.getIns() == (byte) 0xE4) {
                return ApduCategory.DELETE_ADF;
            }
        } else {
            if (isUnsecureApdu(commandApdu)) {
                return ApduCategory.NOT_ALLOWED;
            }
        }

        return ApduCategory.GENERAL;
    }

    private boolean isUnsecureApdu(CommandApdu commandApdu) {
        if (commandApdu.getIns() == (byte) 0xE4) { // DELETE ADF.
            return true;
        }
        return false;
    }

    enum ApduCategory {
        CREATE_ADF,
        MANAGE_ADF,
        IMPORT_ADF,
        DELETE_ADF,
        GENERAL,
        NOT_ALLOWED,
    }

    private void logd(String dbgMsg) {
        Log.d(LOG_TAG, dbgMsg);
    }
}
