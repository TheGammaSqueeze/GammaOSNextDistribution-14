/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.services.telephony.rcs;

/**
 * Various definitions and utilities related to IMS Signalling.
 */
public class ImsSignallingUtils {
    public static final String MMTEL_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-service.ims.icsi.mmtel\"";
    public static final String ONE_TO_ONE_CHAT_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gppservice.ims.icsi.oma.cpm.msg\"";
    public static final String GROUP_CHAT_TAG =
            "+g.3gpp.icsi-ref=\"urn%3Aurn-7%3A3gppservice.ims.icsi.oma.cpm.session\"";
    public static final String FILE_TRANSFER_HTTP_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gppapplication.ims.iari.rcs.fthttp\"";
    public static final String CHATBOT_COMMUNICATION_USING_SESSION_TAG =
            "+g.3gpp.iari-ref=\"urn%3Aurn-7%3A3gpp-application.ims.iari.rcs.chatbot\"";
}
