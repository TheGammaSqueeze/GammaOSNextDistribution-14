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

package android.car.evs;

import android.car.evs.CarEvsBufferDescriptor;
import android.car.evs.CarEvsStatus;
import android.car.evs.ICarEvsStatusListener;
import android.car.evs.ICarEvsStreamCallback;
import android.os.IBinder;

/**
 * @hide
 */
interface ICarEvsService {

    /**
     * Registers a listener to receive changes in CarEvsManager's status.
     */
    void registerStatusListener(in ICarEvsStatusListener listener);

    /**
     * Unregisters a service listener.
     */
    void unregisterStatusListener(in ICarEvsStatusListener listener);

    /**
     * Requests to start a video stream.
     */
    int startVideoStream(int serviceType, in IBinder token, in ICarEvsStreamCallback callback);

    /**
     * Requests to stop an active video stream.
     */
    void stopVideoStream(in ICarEvsStreamCallback callback);

    /**
     * Requests to stop an active video stream from a given service type.
     */
    void stopVideoStreamFrom(in int serviceType, in ICarEvsStreamCallback callback);

    /**
     * Returns the buffer when its usages are done.
     */
    void returnFrameBuffer(in CarEvsBufferDescriptor buffer);

    /**
     * Returns a current status of a given CarEvsService type.
     */
    CarEvsStatus getCurrentStatus(in int serviceType);

    /**
     * Returns a generated session token.
     */
    IBinder generateSessionToken();

    /**
     * Requests to start a camera previewing activity for a given service type.
     */
    int startActivity(int type);

    /**
     * Requests to stop a camera previewing activity, which was launched via startActivity().
     */
    void stopActivity();

    /**
     * Returns whether or not a given service type is supported.
     */
    boolean isSupported(int type);
}
