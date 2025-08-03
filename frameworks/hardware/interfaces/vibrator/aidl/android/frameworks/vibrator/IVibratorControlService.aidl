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

package android.frameworks.vibrator;

import android.frameworks.vibrator.IVibratorController;
import android.frameworks.vibrator.VibrationParam;

/**
 * IVibratorControlService is a service that allows clients to register IVibratorControllers to
 * receive VibrationParams. It also allows the client to set and clear VibrationParams.
 * @hide
 */
@VintfStability
interface IVibratorControlService {
    /**
     * Registers an IVibratorController to allow pushing VibrationParams. These params will be used
     * to modify vibration characteristics, such as scaling.
     * <p>Only one controller should be registered at the same time. Registering a new controller
     * must unregister the old controller before registering the new one.
     *
     * @param controller The vibrator controller used for pulling requests.
     */
    oneway void registerVibratorController(in IVibratorController controller);

    /**
     * Unregisters an IVibratorController.
     * <p>If the provided controller is not the registered one, the request must be ignored.
     *
     * @param controller The vibrator controller to be removed.
     */
    oneway void unregisterVibratorController(in IVibratorController controller);

    /**
     * Sets VibrationParams which will be used to modify some vibration characteristics, such as
     * scaling.
     * <p>If the provided controller is not the registered one, the request must be ignored.
     *
     * @param params The vibration params to be applied to new vibrations.
     * @param token  The token to register a death recipient to expire these params.
     */
    oneway void setVibrationParams(in VibrationParam[] params, in IVibratorController token);

    /**
     * Clears any set VibrationParams and reverts the vibration characteristics to their default
     * settings.
     * <p>If the provided controller is not the registered one, the request must be ignored.
     *
     * @param typesMask The combined bitfield of vibration types to be cleared
     * @param token  The token to register a death recipient to expire these params
     */
    oneway void clearVibrationParams(in int typesMask, in IVibratorController token);

    /**
     * Notifies the VibrationControlService of new VibrationParams.
     * <p>This method must be called by the IVibratorController after processing a
     * 'requestVibrationParams()' call by the service.
     *
     * @param requestToken The token used for this request
     * @param result       The request result
     */
    oneway void onRequestVibrationParamsComplete(
            in IBinder requestToken, in VibrationParam[] result);
}