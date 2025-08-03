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

package com.android.imsserviceentitlement.ts43;

/** Constants to be used in GSMA TS.43 protocol. */
public final class Ts43Constants {
    private Ts43Constants() {}

    /** Node types of XML response content. */
    public static final class ResponseXmlNode {
        private ResponseXmlNode() {}

        /** Node name of token. */
        public static final String TOKEN = "TOKEN";
        /** Node name of application. */
        public static final String APPLICATION = "APPLICATION";
        /** Node name of vers. */
        public static final String VERS = "VERS";
    }

    /** Attribute names of XML response content. */
    public static final class ResponseXmlAttributes {
        private ResponseXmlAttributes() {}

        /** XML attribute name of token. */
        public static final String TOKEN = "token";
        /** XML attribute name of application identifier. */
        public static final String APP_ID = "AppID";
        /** XML attribute name of entitlement status. */
        public static final String ENTITLEMENT_STATUS = "EntitlementStatus";
        /** XML attribute name of E911 address status. */
        public static final String ADDR_STATUS = "AddrStatus";
        /** XML attribute name of terms and condition status. */
        public static final String TC_STATUS = "TC_Status";
        /** XML attribute name of provision status. */
        public static final String PROVISION_STATUS = "ProvStatus";
        /** XML attribute name of entitlement server URL. */
        public static final String SERVER_FLOW_URL = "ServiceFlow_URL";
        /** XML attribute name of entitlement server user data. */
        public static final String SERVER_FLOW_USER_DATA = "ServiceFlow_UserData";
        /** XML attribute name of version. */
        public static final String VERSION = "version";
        /** XML attribute name of validity. */
        public static final String VALIDITY = "validity";
    }
}
