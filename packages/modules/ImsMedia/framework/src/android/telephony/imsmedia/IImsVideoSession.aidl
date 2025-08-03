/**
 * Copyright (c) 2022 The Android Open Source Project
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

import android.telephony.imsmedia.VideoConfig;
import android.telephony.imsmedia.MediaQualityThreshold;
import android.telephony.ims.RtpHeaderExtension;
import android.view.Surface;

/**
 * See ImsVideoSession for more information.
 *
 * {@hide}
 */
interface IImsVideoSession {
    int getSessionId();
    oneway void modifySession(in VideoConfig config);
    oneway void setPreviewSurface(in Surface surface);
    oneway void setDisplaySurface(in Surface surface);
    oneway void sendHeaderExtension(in List<RtpHeaderExtension> extensions);
    oneway void setMediaQualityThreshold(in MediaQualityThreshold threshold);
    oneway void requestVideoDataUsage();
}

