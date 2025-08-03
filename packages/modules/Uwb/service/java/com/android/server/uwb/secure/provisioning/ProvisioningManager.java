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

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;

import com.android.server.uwb.secure.SecureElementChannel;
import com.android.server.uwb.secure.csml.DeleteAdfCommand;
import com.android.server.uwb.secure.csml.DeleteAdfResponse;
import com.android.server.uwb.util.ObjectIdentifier;

import java.io.IOException;
import java.util.UUID;

/** Manages the ADF provisioning and deleting. */
public class ProvisioningManager {
    private static final String LOG_TAG = "ProvisioningManager";

    @NonNull
    private final SecureElementChannel mSecureElementChannel;
    @NonNull
    private Handler mWorkHandler;

    /** Constructors of {@link ProvisioningManager} */
    public ProvisioningManager(@NonNull SecureElementChannel secureElementChannel,
            @NonNull Looper workLooper) {

        this.mSecureElementChannel = secureElementChannel;
        this.mWorkHandler = new Handler(workLooper);
    }

    /** Provisions the ADF with the signed script file which is encoded as PKCS#7 CMS. */
    public void provisioningAdf(@NonNull UUID serviceInstanceId, @NonNull byte[] scriptData,
            @NonNull ProvisioningCallback provisioningCallback) {
        mWorkHandler.post(() -> {
            try {
                ScriptParser.ScriptContent scriptContent =
                        ScriptParser.parseSignedScript(scriptData);
                ScriptRunner engine = new ScriptRunner(mSecureElementChannel);
                engine.run(scriptContent, serviceInstanceId, provisioningCallback);
            } catch (ProvisioningException e) {
                provisioningCallback.onFail(serviceInstanceId);
            }
        });
    }

    /** Deletes the specified ADF in applet. */
    public void deleteAdf(@NonNull UUID serviceInstanceId, @NonNull ObjectIdentifier adfOid,
            @NonNull DeleteAdfCallback deleteAdfCallback) {
        mWorkHandler.post(() -> {
            DeleteAdfCommand deleteAdfCommand = DeleteAdfCommand.build(adfOid);
            try {
                if (!mSecureElementChannel.openChannel()) {
                    throw new IllegalStateException("cannot open se channel.");
                }
                DeleteAdfResponse response = DeleteAdfResponse.fromResponseApdu(
                        mSecureElementChannel.transmit(deleteAdfCommand));

                if (!response.isSuccess()) {
                    throw new IllegalStateException("error from applet: " + response.statusWord);
                }

                deleteAdfCallback.onSuccess(serviceInstanceId, adfOid);
            } catch (IOException | IllegalStateException e) {
                logw("DeleteAdf: error - " + e);
                deleteAdfCallback.onFail(serviceInstanceId, adfOid);
            }
        });
    }

    /** The callback about the result of the provisioning script. */
    public interface ProvisioningCallback {

        /** ADF was created in applet. */
        void onAdfCreated(@NonNull UUID serviceInstanceId, @NonNull ObjectIdentifier adfOid);

        /** ADF was provisioned in applet. */
        void onAdfProvisioned(@NonNull UUID serviceInstanceId, @NonNull ObjectIdentifier adfOid);

        /** ADF was created and provisioned, the content should be serialized out of the applet. */
        void onAdfImported(@NonNull UUID serviceInstanceId, @NonNull ObjectIdentifier adfOid,
                @NonNull byte[] secureBlob);

        /** ADF in applet was deleted. */
        void onAdfDeleted(@NonNull UUID serviceInstanceId, @NonNull ObjectIdentifier adfOid);

        /** The script was not executed successfully. */
        void onFail(@NonNull UUID serviceInstanceId);
    }

    /** Callback for the deleting ADF operation. */
    public interface DeleteAdfCallback {
        /** The specified ADF was deleted. */
        void onSuccess(UUID serviceInstanceId, ObjectIdentifier adfOid);

        /** The specified ADF wasn't deleted. */
        void onFail(UUID serviceInstanceId, ObjectIdentifier adfOid);
    }

    private void logw(String debugMsg) {
        Log.w(LOG_TAG, debugMsg);
    }
}
