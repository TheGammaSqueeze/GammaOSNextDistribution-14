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

package android.telephony.mockmodem;

public class MockMessagingService {
    private static final String TAG = "MockMessagingService";
    private String mTag;

    // Setup messaging result parameters
    String mSmsc;
    int mPhoneId;


    public MockMessagingService(int instanceId) {
        mTag = TAG + "-" + instanceId;
        mPhoneId = instanceId;
        initializeParameter();
    }

    private void initializeParameter() {
        mSmsc = "1234560000@ims.mnc.org";
    }

    public String getSmscAddress() {
        return mSmsc;
    }
}
