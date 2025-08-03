/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.net.wifi;

/**
 * Interface for target wake time (TWT) callback
 *
 * @hide
 */
oneway interface ITwtCallback
{
    /**
     * Called when a TWT session setup operation fails.
     *
     * @param errorCode setup error code
     * @hide
     */
    void onFailure(int errorCode);
    /**
     * Called when a TWT session is torndown. Can be called as a response to
     * {@link TwtSession#teardown()} or unsolicited. Check the {@link TwtReasonCode} for more
     * details.
     *
     * @param reasonCode Teardown reason code
     * @hide
     */
    void onTeardown(int reasonCode);
    /**
     * Called when the TWT session is created.
     *
     * @param wakeDuration TWT session wake duration
     * @param wakeInterval TWT session wake interval
     * @param mloLinkId Multi link operation link id
     * @param owner Owner of this session
     * @param sessionId TWT session id
     * @hide
     */
    void onCreate(int wakeDuration, long wakeInterval, int mloLinkId, int owner, int sessionId);
}
