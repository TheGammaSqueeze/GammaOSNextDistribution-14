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

package hardware.google.ril_ext;

@VintfStability
interface IRilExtIndication {
    /**
     * Register or unregister the interested carrier configurations.
     *
     * @param registeredKeys A list of the interested carrier configurations
     * @param unregisteredKeys A list of the uninterested carrier configurations
     */
    void registerCarrierConfigChange(in String[] registeredKeys, in String[] unregisteredKeys);
    /**
     * Trigger bugreport to report RIL issues.
     *
     * @param title The title of the bugreport
     */
    void triggerBugreport(in String title);
}
