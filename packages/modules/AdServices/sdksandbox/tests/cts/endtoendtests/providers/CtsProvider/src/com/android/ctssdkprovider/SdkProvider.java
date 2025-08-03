/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.ctssdkprovider;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;

public class SdkProvider extends SandboxedSdkProvider {

    private static final String TEST_OPTION = "test-option";
    private static final String OPTION_THROW_INTERNAL_ERROR = "internal-error";
    private static final String OPTION_THROW_REQUEST_SURFACE_PACKAGE_ERROR = "rsp-error";

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {

        String testOption = params.getString(TEST_OPTION);
        if (TextUtils.equals(testOption, OPTION_THROW_INTERNAL_ERROR)) {
            throw new RuntimeException("General Exception");
        }
        return new SandboxedSdk(new CtsSdkProviderApiImpl(getContext()));
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        String testOption = params.getString(TEST_OPTION);
        if (TextUtils.equals(testOption, OPTION_THROW_REQUEST_SURFACE_PACKAGE_ERROR)) {
            throw new RuntimeException("General Exception");
        }
        return new View(windowContext);
    }
}
