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

package com.google.uwb.support.ccc;

import android.os.Build.VERSION_CODES;
import android.os.PersistableBundle;

import androidx.annotation.RequiresApi;

import com.google.uwb.support.base.RequiredParam;

/**
 * Defines parameters for CCC error reports
 *
 * <p>This passed as a bundle to the following callbacks, if the reason is {@link
 * RangingSession.Callback.Reason#REASON_PROTOCOL_SPECIFIC_ERROR}:
 *
 * <ul>
 *   <li>{@link RangingSession.Callback#onOpenFailed}
 *   <li>{@link RangingSession.Callback#onStartFailed}
 *   <li>{@link RangingSession.Callback#onReconfigureFailed}
 *   <li>{@link RangingSession.Callback#onStopFailed}
 *   <li>Any other {@code on*Failed} callback method.
 * </ul>
 */
@RequiresApi(VERSION_CODES.LOLLIPOP)
public class CccRangingError extends CccParams {
    @ProtocolError private final int mError;

    private static final String KEY_ERROR_CODE = "error_code";

    private static final int BUNDLE_VERSION_1 = 1;
    private static final int BUNDLE_VERSION_CURRENT = BUNDLE_VERSION_1;

    private CccRangingError(@ProtocolError int error) {
        mError = error;
    }

    @Override
    protected int getBundleVersion() {
        return BUNDLE_VERSION_CURRENT;
    }

    public static CccRangingError fromBundle(PersistableBundle bundle) {
        if (!isCorrectProtocol(bundle)) {
            throw new IllegalArgumentException("Invalid protocol or protocol version");
        }

        switch (getBundleVersion(bundle)) {
            case BUNDLE_VERSION_1:
                return parseVersion1(bundle);

            default:
                throw new IllegalArgumentException("Invalid bundle version");
        }
    }

    private static CccRangingError parseVersion1(PersistableBundle bundle) {
        return new Builder().setError(bundle.getInt(KEY_ERROR_CODE)).build();
    }

    @Override
    public PersistableBundle toBundle() {
        PersistableBundle bundle = super.toBundle();
        bundle.putInt(KEY_ERROR_CODE, mError);
        return bundle;
    }

    @ProtocolError
    public int getError() {
        return mError;
    }

    /** Builder */
    public static final class Builder {
        @ProtocolError private RequiredParam<Integer> mError = new RequiredParam<>();

        public Builder setError(@ProtocolError int error) {
            mError.set(error);
            return this;
        }

        public CccRangingError build() {
            return new CccRangingError(mError.get());
        }
    }
}
