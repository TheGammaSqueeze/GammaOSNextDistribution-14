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

package android.display.cts;

import android.app.Activity;
import android.os.Bundle;
import android.view.WindowManager;

import androidx.annotation.Nullable;

/**
 * Test activity to verify HDR output conversion.
 */
public class HdrConversionTestActivity extends Activity {

    private static final String DISABLE_HDR_CONVERSION = "disable_hdr_conversion";
    private boolean mDisableHdrConversion = false;

    @Override
    public void onSaveInstanceState(Bundle outBundle) {
        super.onSaveInstanceState(outBundle);

        outBundle.putBoolean(DISABLE_HDR_CONVERSION, mDisableHdrConversion);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            mDisableHdrConversion = savedInstanceState.getBoolean(DISABLE_HDR_CONVERSION, false);
            updateLayoutParams();
        }
    }

    /** Disable the HDR output conversion. This is called directly from test instrumentation. */
    public void disableHdrConversion(boolean disableHdrConversion) {
        mDisableHdrConversion = disableHdrConversion;
        updateLayoutParams();
    }

    private void updateLayoutParams() {
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.setHdrConversionEnabled(!mDisableHdrConversion);
        getWindow().setAttributes(params);
    }
}
