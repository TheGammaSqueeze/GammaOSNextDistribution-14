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

package com.android.codeproviderresources_1;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Bundle;
import android.view.View;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class ResourceSandboxedSdkProvider extends SandboxedSdkProvider {

    private static final String STRING_RESOURCE = "Test String";
    private static final int INTEGER_RESOURCE = 1234;
    private static final String STRING_ASSET = "This is a test asset";
    private static final String ASSET_FILE = "test-asset.txt";

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {
        Resources resources = getContext().getResources();
        String stringRes = resources.getString(R.string.test_string);
        int integerRes = resources.getInteger(R.integer.test_integer);
        if (!stringRes.equals(STRING_RESOURCE)) {
            throw new LoadSdkException(
                    new Throwable(createErrorMessage(STRING_RESOURCE, stringRes)), new Bundle());
        }
        if (integerRes != INTEGER_RESOURCE) {
            throw new LoadSdkException(
                    new Throwable(
                            createErrorMessage(
                                    String.valueOf(INTEGER_RESOURCE), String.valueOf(integerRes))),
                    new Bundle());
        }

        AssetManager assets = getContext().getAssets();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(assets.open(ASSET_FILE)))) {
            String readAsset = reader.readLine();
            if (!readAsset.equals(STRING_ASSET)) {
                throw new LoadSdkException(
                        new Throwable(createErrorMessage(STRING_ASSET, readAsset)), new Bundle());
            }
        } catch (IOException e) {
            throw new LoadSdkException(
                    new Throwable("File not found: " + ASSET_FILE), new Bundle());
        }
        return new SandboxedSdk(new Binder());
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        return null;
    }

    /* Sends an error if the expected resource/asset does not match the read value. */
    private String createErrorMessage(String expected, String actual) {
        return new String("Expected " + expected + ", actual " + actual);
    }
}
