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
package android.car.oem;

import android.annotation.Nullable;
import android.annotation.SystemApi;

import java.io.PrintWriter;

/**
 * Contains life cycle methods for the OEM Service.
 *
 * <p>This is to enforce structure on the OEM Service components. {link OemCarService} would call
 * these methods.
 *
 * @hide
 */
@SystemApi
@SuppressWarnings("[NotCloseable]")
public interface OemCarServiceComponent {
    /**
     * Initializes required resources.
     *
     * <p>This is called for each service component during {@link OemCarService#onCreate()}
     * call of OemCarService.
     */
    void init();

    /**
     * Releases required resources.
     *
     * <p>This is called for each service component during {@link OemCarService#onDestroy()}
     * call of OemCarService.
     */
    void release();

    /**
     * Dumps the service component details.
     *
     * <p>Each service component should implement a dump command to dump. It is called from
     * {@link OemCarService#dump(java.io.FileDescriptor, PrintWriter, String[])} call.
     */
    void dump(@Nullable PrintWriter writer, @Nullable String[] args);

    /**
     * Informs if CarService is ready.
     *
     * <p> Each service component should do the necessary initialization depending on CarService. It
     * is called from {@link OemCarService#onCarServiceReady()} call.
     */
    void onCarServiceReady();
}
