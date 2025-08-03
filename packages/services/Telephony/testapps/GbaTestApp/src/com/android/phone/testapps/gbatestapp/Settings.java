/*
 * Copyright 2020 The Android Open Source Project
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

package com.android.phone.testapps.gbatestapp;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.RemoteException;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyFrameworkInitializer;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.telephony.ITelephony;

import java.util.Locale;

/** class to load and save the settings */
public class Settings {

    private static final String TAG = "SETTINGS";

    private static final String PREF_CARRIER_CONFIG = "pref_carrier_config";
    private static final String KEY_SERVICE_PACKAGE = "key_service_package";
    private static final String KEY_RELEASE_TIME = "key_release_time";

    private static final String PREF_TEST_CONFIG = "pref_test_config";
    private static final String KEY_APP_TYPE = "key_app_type";
    private static final String KEY_NAF_URL = "key_naf_url";
    private static final String KEY_FORCE_BT = "key_force_bt";
    private static final String KEY_ORG = "org";
    private static final String KEY_SP_ID = "key_sp_id";
    private static final String KEY_TLS_CS = "key_tls_cs";

    private static final String PREF_SERVICE_CONFIG = "pref_carrier_config";
    private static final String KEY_AUTH_RESULT = "key_auth_result";
    private static final String KEY_GBA_KEY = "key_gba_key";
    private static final String KEY_B_TID = "key_b_tid";
    private static final String KEY_FAIL_REASON = "key_fail_reason";

    private ITelephony mTelephony;
    private int mSubId;
    private String mServicePackageName;
    private int mReleaseTime;
    private int mAppType;
    private String mNafUrl;
    private boolean mForceBootstrap;
    private int mOrg;
    private int mSpId;
    private int mTlsCs;
    private boolean mIsAuthSuccess;
    private String mGbaKey;
    private String mBTid;
    private int mFailReason;

    private static Settings sInstance;

    private Settings(Context cxt) {
        mTelephony = ITelephony.Stub.asInterface(TelephonyFrameworkInitializer
                .getTelephonyServiceManager().getTelephonyServiceRegisterer().get());
        mSubId = SubscriptionManager.getDefaultSubscriptionId();
        SharedPreferences sharedPref = cxt.getSharedPreferences(
                PREF_CARRIER_CONFIG, Context.MODE_PRIVATE);
        mServicePackageName = loadServicePackageName(mSubId, sharedPref);
        mReleaseTime = loadReleaseTime(mSubId, sharedPref);

        sharedPref = cxt.getSharedPreferences(PREF_TEST_CONFIG, Context.MODE_PRIVATE);
        mAppType = sharedPref.getInt(KEY_APP_TYPE, TelephonyManager.APPTYPE_SIM);
        mNafUrl = sharedPref.getString(KEY_NAF_URL, null);
        mForceBootstrap = sharedPref.getBoolean(KEY_FORCE_BT, false);
        mOrg = sharedPref.getInt(KEY_ORG, 0);
        mSpId = sharedPref.getInt(KEY_SP_ID, 0);
        mTlsCs = sharedPref.getInt(KEY_TLS_CS, 0);

        sharedPref = cxt.getSharedPreferences(PREF_SERVICE_CONFIG, Context.MODE_PRIVATE);
        mIsAuthSuccess = sharedPref.getBoolean(KEY_AUTH_RESULT, false);
        mFailReason = sharedPref.getInt(KEY_FAIL_REASON, 0);
        mGbaKey = sharedPref.getString(KEY_GBA_KEY, null);
        mBTid = sharedPref.getString(KEY_B_TID, null);
    }

    /** Get the instance of Settings*/
    public static Settings getSettings(Context cxt) {
        if (sInstance == null) {
            sInstance = new Settings(cxt);
        }

        return sInstance;
    }

    /** update carrier config settings */
    public void updateCarrierConfig(Context cxt, String packageName, int releaseTime) {
        new Thread(() -> {
            synchronized (PREF_CARRIER_CONFIG) {

                if (TextUtils.equals(mServicePackageName, packageName)
                        && (mReleaseTime == releaseTime)) {
                    return;
                }

                if (!TextUtils.equals(mServicePackageName, packageName)) {
                    mServicePackageName = packageName;

                    try {
                        mTelephony.setBoundGbaServiceOverride(mSubId, packageName);
                    } catch (RemoteException e) {
                        Log.e(TAG, "fail to set package name due to " + e);
                    }

                }

                if (mReleaseTime != releaseTime) {
                    mReleaseTime = releaseTime;

                    try {
                        mTelephony.setGbaReleaseTimeOverride(mSubId, releaseTime);
                    } catch (RemoteException e) {
                        Log.e(TAG, "fail to set release time due to " + e);
                    }
                }

                SharedPreferences sharedPref = cxt.getSharedPreferences(
                        PREF_CARRIER_CONFIG, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(KEY_SERVICE_PACKAGE, packageName);
                editor.putInt(KEY_RELEASE_TIME, releaseTime);
                editor.commit();
            }
        }).start();
    }

    /** get the config of gba service package name */
    public String getServicePackageName() {
        synchronized (PREF_CARRIER_CONFIG) {
            return mServicePackageName;
        }
    }

    /** get the config of gba release time */
    public int getReleaseTime() {
        synchronized (PREF_CARRIER_CONFIG) {
            return mReleaseTime;
        }
    }

    /** get the config of gba service package name used for now*/
    public String loadServicePackageName(int subId, SharedPreferences sharedPref) {
        try {
            return mTelephony.getBoundGbaService(subId);
        } catch (RemoteException e) {
            Log.e(TAG, "fail to get package name due to " + e);
        }
        return sharedPref != null ? sharedPref.getString(KEY_SERVICE_PACKAGE, null) : null;
    }

    /** get the config of gba release time used for now */
    public int loadReleaseTime(int subId, SharedPreferences sharedPref) {
        try {
            return mTelephony.getGbaReleaseTime(subId);
        } catch (RemoteException e) {
            Log.e(TAG, "fail to get package name due to " + e);
        }
        return sharedPref != null ? sharedPref.getInt(KEY_RELEASE_TIME, 0) : 0;
    }

    /** update the config of test gba service */
    public void updateServiceConfig(Context cxt, boolean success, int reason,
                String key, String btId) {
        new Thread(() -> {
            synchronized (PREF_SERVICE_CONFIG) {
                mIsAuthSuccess = success;
                mFailReason = reason;
                mGbaKey = key;
                mBTid = btId;
                SharedPreferences sharedPref = cxt.getSharedPreferences(
                        PREF_SERVICE_CONFIG, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putBoolean(KEY_AUTH_RESULT, success);
                editor.putInt(KEY_FAIL_REASON, reason);
                editor.putString(KEY_GBA_KEY, key);
                editor.putString(KEY_B_TID, btId);
                editor.commit();
            }
        }).start();
    }

    /** get the config of the authentication result */
    public boolean getAuthResult() {
        synchronized (PREF_SERVICE_CONFIG) {
            return mIsAuthSuccess;
        }
    }

    /** get the config of authentication fail cause */
    public int getFailReason() {
        synchronized (PREF_SERVICE_CONFIG) {
            return mFailReason;
        }
    }

    /** get the config of GBA key */
    public String getGbaKey() {
        synchronized (PREF_SERVICE_CONFIG) {
            return mGbaKey;
        }
    }

    /** get the config of B-Tid */
    public String getBTid() {
        synchronized (PREF_SERVICE_CONFIG) {
            return mBTid;
        }
    }

    /** update the config of the test */
    public void updateTestConfig(Context cxt, int appType, String url,
            boolean force, int org, int spId, int tlsCs) {
        new Thread(() -> {
            synchronized (PREF_TEST_CONFIG) {
                mAppType = appType;
                mNafUrl = url;
                mForceBootstrap = force;
                mOrg = org;
                mSpId = spId;
                mTlsCs = tlsCs;

                SharedPreferences sharedPref = cxt.getSharedPreferences(
                        PREF_TEST_CONFIG, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putInt(KEY_APP_TYPE, appType);
                editor.putString(KEY_NAF_URL, url);
                editor.putBoolean(KEY_FORCE_BT, force);
                editor.putInt(KEY_ORG, org);
                editor.putInt(KEY_SP_ID, spId);
                editor.putInt(KEY_TLS_CS, tlsCs);
                editor.commit();
            }
        }).start();
    }

    /** get the config of the uicc application type*/
    public int getAppType() {
        synchronized (PREF_TEST_CONFIG) {
            return mAppType;
        }
    }

    /** get the config of NAF url */
    public String getNafUrl() {
        synchronized (PREF_TEST_CONFIG) {
            return mNafUrl;
        }
    }

    /** get the config if bootstrap is forced */
    public boolean isForceBootstrap() {
        synchronized (PREF_TEST_CONFIG) {
            return mForceBootstrap;
        }
    }

    /** get the config of the organization code */
    public int getOrg() {
        synchronized (PREF_TEST_CONFIG) {
            return mOrg;
        }
    }

    /** get the config of the security protocol id */
    public int getSpId() {
        synchronized (PREF_TEST_CONFIG) {
            return mSpId;
        }
    }

    /** get the config of the tls ciper suite id */
    public int getTlsCs() {
        synchronized (PREF_TEST_CONFIG) {
            return mTlsCs;
        }
    }

    /** convert byte arry to hex string */
    public static String byteArrayToHexString(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (byte b : data) {
            sb.append(String.format(Locale.US, "%02X", b));
        }
        return sb.toString();
    }
}
