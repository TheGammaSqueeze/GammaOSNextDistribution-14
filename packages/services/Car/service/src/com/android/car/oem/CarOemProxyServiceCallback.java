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

package com.android.car.oem;

/**
 * Callback for other CarService components when OEM service is ready.
 *
 * <p>Other CarService components are not expected to call OEM Service during init process. If they
 * need to make call to OEM service as soon as OEM service is ready, then can register this
 * callback to {link CarOemProxyService}. This callback will be called for all callback registered
 * during init calls. The callback will be made only once after OEM service is connected.
 */
public interface CarOemProxyServiceCallback {
    /**
     * Called when OEM service is ready.
     */
    void onOemServiceReady();

}
