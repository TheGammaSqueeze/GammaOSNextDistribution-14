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

package com.google.uwb.support.oemextension;

import android.os.PersistableBundle;
import android.uwb.UwbManager;

import com.google.uwb.support.base.RequiredParam;

/**
 * Session config parameters for oem extension callback.
 *
 * <p> This is passed as a bundle to oem extension API
 * {@link UwbManager.UwbOemExtensionCallback#onSessionConfigurationComplete(PersistableBundle)}
 */
public class SessionConfigParams {

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;
    private final long mSessionId;
    private final int mSessionToken;
    private final PersistableBundle mFiraOpenSessionParamsBundle;

    public static final String KEY_BUNDLE_VERSION = "bundle_version";
    public static final String SESSION_ID = "session_id";
    public static final String SESSION_TOKEN = "session_token";
    public static final String OPEN_SESSION_PARAMS_BUNDLE = "open_session_params_bundle";

    public SessionConfigParams(long sessionId, int sessionToken,
            PersistableBundle firaOpenSessionParamsBundle) {
        mSessionId = sessionId;
        mSessionToken = sessionToken;
        mFiraOpenSessionParamsBundle = firaOpenSessionParamsBundle;
    }

    public static int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public long getSessionId() {
        return mSessionId;
    }

    public int getSessionToken() {
        return mSessionToken;
    }

    public PersistableBundle getFiraOpenSessionParamsBundle() {
        return mFiraOpenSessionParamsBundle;
    }

    public PersistableBundle toBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putInt(KEY_BUNDLE_VERSION, getBundleVersion());
        bundle.putLong(SESSION_ID, mSessionId);
        bundle.putInt(SESSION_TOKEN, mSessionToken);
        bundle.putPersistableBundle(OPEN_SESSION_PARAMS_BUNDLE, mFiraOpenSessionParamsBundle);
        return bundle;
    }

    public static boolean isSessionConfigParams(PersistableBundle bundle) {
        return bundle.containsKey(OPEN_SESSION_PARAMS_BUNDLE);
    }

    public static SessionConfigParams fromBundle(PersistableBundle bundle) {
        switch (bundle.getInt(KEY_BUNDLE_VERSION)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);
            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static SessionConfigParams parseVersion1(PersistableBundle bundle) {
        return new SessionConfigParams.Builder()
                .setSessionId(bundle.getLong(SESSION_ID))
                .setSessiontoken(bundle.getInt(SESSION_TOKEN))
                .setOpenSessionParamsBundle(bundle.getPersistableBundle(OPEN_SESSION_PARAMS_BUNDLE))
                .build();
    }

    /** Builder */
    public static class Builder {
        private final RequiredParam<Long> mSessionId = new RequiredParam<>();
        private int mSessionToken = 0;
        private RequiredParam<PersistableBundle> mOpenSessionParamsBundle = new RequiredParam<>();

        public SessionConfigParams.Builder setSessionId(long sessionId) {
            mSessionId.set(sessionId);
            return this;
        }

        public SessionConfigParams.Builder setSessiontoken(int sessionToken) {
            mSessionToken = sessionToken;
            return this;
        }

        public SessionConfigParams.Builder setOpenSessionParamsBundle(PersistableBundle bundle) {
            mOpenSessionParamsBundle.set(bundle);
            return this;
        }

        public SessionConfigParams build() {
            return new SessionConfigParams(mSessionId.get(), mSessionToken,
                    mOpenSessionParamsBundle.get());
        }
    }
}
