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

package com.google.android.car.multidisplaytest.occupantconnection;

final class Constants {

    private Constants() {
    }

    static final String KEY_OCCUPANT_ZONE = "occupant_zone";
    static final String KEY_MESSAGE_RECEIVER = "message_receiver";
    static final String SYSTEM_PROPERTY_KEY_CONNECTION_NEEDS_USER_APPROVAL =
            "multidisplaytest.occupantconnection.need_approval";
    static final String ACTION_CONNECTION_CANCELLED =
            "multidisplaytest.occupantconnection.connection_cancelled";
    static final String TEXT_RECEIVER_ID = "TextReceiverFragment";
    static final String SEEK_BAR_RECEIVER_ID = "SeekBarReceiverFragment";

    static final int ACCEPTED_RESULT_CODE = 1;
    static final int REJECTED_RESULT_CODE = 2;
}
