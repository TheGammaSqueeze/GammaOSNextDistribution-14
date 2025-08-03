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

package android.car.builtin.app;

import android.annotation.SystemApi;
import android.car.builtin.os.ServiceManagerHelper;
import android.content.Context;
import android.os.RemoteException;

import com.android.internal.app.IVoiceInteractionManagerService;

/**
 * Helper for VoiceInteractionManagerService related operations.
 *
 * @hide
 */
@SystemApi(client = SystemApi.Client.MODULE_LIBRARIES)
public final class VoiceInteractionHelper {

    /** Checks if ${link VoiceInteractionManagerService} is available. */
    public static boolean isAvailable() {
        return getService() != null;
    }

    /**
     * Enables/disables voice interaction.
     *
     * @param enabled Whether to enable or disable voice interaction.
     */
    public static void setEnabled(boolean enabled) throws RemoteException {
        IVoiceInteractionManagerService service = getService();
        if (service == null) {
            return;
        }
        service.setDisabled(!enabled);
    }

    private static IVoiceInteractionManagerService getService() {
        return IVoiceInteractionManagerService.Stub.asInterface(ServiceManagerHelper
                .getService(Context.VOICE_INTERACTION_MANAGER_SERVICE));
    }

    /** Constructs {@link VoiceInteractionHelper}. */
    private VoiceInteractionHelper() {
        throw new UnsupportedOperationException("contains only static members");
    }
}
