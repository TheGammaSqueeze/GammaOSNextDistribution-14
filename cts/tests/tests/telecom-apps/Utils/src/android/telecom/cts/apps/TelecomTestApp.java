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

package android.telecom.cts.apps;

import android.content.ComponentName;
import android.os.Parcel;
import android.os.Parcelable;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;

/**
 * Each enum in TelecomTestApp represents an application in the cts/tests/tests/telecom-apps
 * directory.
 */
public enum TelecomTestApp implements Parcelable {
    TransactionalVoipAppMain,
    TransactionalVoipAppClone,
    ConnectionServiceVoipAppMain,
    ConnectionServiceVoipAppClone,
    ManagedConnectionServiceApp;

    /**
     * General TestApp info
     */
    public static final String DEFAULT_ID = "1";
    public static final String CUSTOM_ID = "2";

    /**
     * ManagedConnectionServiceApp
     */
    public static final String MANAGED_APP_ID = "ManagedApp_1";
    public static final String MANAGED_PACKAGE_NAME = "android.telecom.cts.apps.managedapp";
    public static final String MANAGED_CONNECTION_SERVICE_NAME =
            MANAGED_PACKAGE_NAME + ".ManagedConnectionService";
    public static final ComponentName MANAGED_APP_CN = new ComponentName(
            MANAGED_PACKAGE_NAME, MANAGED_CONNECTION_SERVICE_NAME
    );
    public static final String CONTROL_INTERFACE_ACTION = MANAGED_PACKAGE_NAME + ".BIND";
    public static final String MANAGED_ADDRESS = "tel:555-TEST-sim2";
    public static final String MANAGED_APP_LABEL = "test label";


    /**
     * ConnectionServiceVoipApp*
     */
    public static final String SELF_MANAGED_CS_MAIN_PACKAGE_NAME =
            "android.telecom.cts.apps.connectionservicevoipappmain";
    public static final String SELF_MANAGED_CS_CLONE_PACKAGE_NAME =
            "android.telecom.cts.apps.connectionservicevoipappclone";

    public static final String SELF_MANAGED_CS_MAIN_SERVICE =
            SELF_MANAGED_CS_MAIN_PACKAGE_NAME + ".VoipConnectionServiceMain";
    public static final String SELF_CLONE_CS_MAIN_SERVICE =
            SELF_MANAGED_CS_CLONE_PACKAGE_NAME + ".VoipConnectionServiceClone";



    public static final String VOIP_CS_CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.apps.connectionservicevoipapp.BIND";

    public static final PhoneAccountHandle SELF_MANAGED_CS_MAIN_HANDLE = new PhoneAccountHandle(
            new ComponentName(SELF_MANAGED_CS_MAIN_PACKAGE_NAME,
                    SELF_MANAGED_CS_MAIN_SERVICE), DEFAULT_ID);

    public static final PhoneAccountHandle SELF_MANAGED_CS_MAIN_CUSTOM_HANDLE =
            new PhoneAccountHandle(
                    new ComponentName(SELF_MANAGED_CS_MAIN_PACKAGE_NAME,
                            SELF_MANAGED_CS_MAIN_SERVICE), CUSTOM_ID);

    public static final PhoneAccountHandle SELF_MANAGED_CS_CLONE_HANDLE =
            new PhoneAccountHandle(
                    new ComponentName(SELF_MANAGED_CS_CLONE_PACKAGE_NAME,
                            SELF_CLONE_CS_MAIN_SERVICE), DEFAULT_ID);

    public static final PhoneAccount SELF_MANAGED_CS_MAIN_ACCOUNT =
            PhoneAccount.builder(SELF_MANAGED_CS_MAIN_HANDLE,
                            "SelfManaged_Main D-Label")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SELF_MANAGED
                    ).build();

    public static final PhoneAccount SELF_MANAGED_CS_MAIN_ACCOUNT_CUSTOM =
            PhoneAccount.builder(SELF_MANAGED_CS_MAIN_CUSTOM_HANDLE,
                            "SelfManaged_Main C-Label")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SELF_MANAGED
                    ).build();

    public static final PhoneAccount SELF_MANAGED_CS_CLONE_ACCOUNT =
            PhoneAccount.builder(SELF_MANAGED_CS_CLONE_HANDLE,
                            "SelfManaged_Clone D-Label")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SELF_MANAGED
                    ).build();

    /**
     * TransactionalVoipApp*
     */
    public static final String TRANSACTIONAL_PACKAGE_NAME =
            "android.telecom.cts.apps.transactionalvoipappmain";
    public static final String TRANSACTIONAL_CLONE_PACKAGE_NAME =
            "android.telecom.cts.apps.transactionalvoipappclone";
    public static final String T_CONTROL_INTERFACE_ACTION =
            "android.telecom.cts.apps.transactionalvoipapp.BIND";


    public static final PhoneAccountHandle TRANSACTIONAL_APP_DEFAULT_HANDLE =
            new PhoneAccountHandle(
                    new ComponentName(TRANSACTIONAL_PACKAGE_NAME, TRANSACTIONAL_PACKAGE_NAME),
                    DEFAULT_ID);

    public static final PhoneAccountHandle TRANSACTIONAL_APP_SUPPLEMENTARY_HANDLE =
            new PhoneAccountHandle(
                    new ComponentName(TRANSACTIONAL_PACKAGE_NAME, TRANSACTIONAL_PACKAGE_NAME),
                    CUSTOM_ID);

    public static final PhoneAccountHandle TRANSACTIONAL_APP_CLONE_DEFAULT_HANDLE =
            new PhoneAccountHandle(
                    new ComponentName(TRANSACTIONAL_CLONE_PACKAGE_NAME,
                            TRANSACTIONAL_CLONE_PACKAGE_NAME),
                    CUSTOM_ID);

    public static final PhoneAccount TRANSACTIONAL_MAIN_DEFAULT_ACCOUNT =
            PhoneAccount.builder(TRANSACTIONAL_APP_DEFAULT_HANDLE, "T-Label MAIN")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                                    | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                                    | PhoneAccount.CAPABILITY_VIDEO_CALLING
                    ).build();

    public static final PhoneAccount TRANSACTIONAL_MAIN_SUPPLEMENTARY_ACCOUNT =
            PhoneAccount.builder(TRANSACTIONAL_APP_SUPPLEMENTARY_HANDLE, "T-Label MAIN supp.")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                                    | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                                    | PhoneAccount.CAPABILITY_VIDEO_CALLING
                                    | PhoneAccount.CAPABILITY_SELF_MANAGED
                    ).build();

    public static final PhoneAccount TRANSACTIONAL_CLONE_ACCOUNT =
            PhoneAccount.builder(TRANSACTIONAL_APP_CLONE_DEFAULT_HANDLE, "T-Label CLONE")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_SUPPORTS_TRANSACTIONAL_OPERATIONS
                                    | PhoneAccount.CAPABILITY_SUPPORTS_VIDEO_CALLING
                                    | PhoneAccount.CAPABILITY_VIDEO_CALLING
                    ).build();

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(toInteger());
    }

    public static final Creator<TelecomTestApp> CREATOR = new Creator<>() {
        @Override
        public TelecomTestApp createFromParcel(Parcel in) {
            return TelecomTestApp.fromInteger(in.readInt());
        }

        @Override
        public TelecomTestApp[] newArray(int size) {
            return new TelecomTestApp[size];
        }
    };

    public int toInteger() {
        return this.ordinal();
    }

    public static TelecomTestApp fromInteger(int value) {
        return values()[value];
    }
}
