/**
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

package android.telephony.imsmedia;

import android.os.ParcelFileDescriptor;
import android.telephony.imsmedia.RtpConfig;
import android.telephony.imsmedia.VideoConfig;

/**
 * See ImsMediaManager for more information
 *
 * {@hide}
 */
oneway interface IImsMedia {
    void openSession(
        in ParcelFileDescriptor rtpFd,
        in ParcelFileDescriptor rtcpFd,
        int sessionType,
        in RtpConfig rtpConfig,
        in IBinder callback);

    void closeSession(in IBinder session);

    /**
    * Generates the array of SPROP strings for the given array of video
    * configurations and returns via IImsMediaCallback.
    **/
    void generateVideoSprop(in VideoConfig[] videoConfigList, in IBinder callback);
}

