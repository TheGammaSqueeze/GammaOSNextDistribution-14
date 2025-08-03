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

package com.android.emptysdkprovider;

import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.os.Binder;
import android.os.Bundle;
import android.view.View;

/*
This Empty Provider is loaded into the sandbox in testing scenarios where we wish to keep the
sandbox process alive even when all other test sdk providers are unloaded. The Empty Provider hence
is not required to attach to any views.
 */
public class EmptySdkProvider extends SandboxedSdkProvider {

    @Override
    public final SandboxedSdk onLoadSdk(Bundle params) {
        return new SandboxedSdk(new Binder());
    }

    @Override
    public final View getView(Context windowContext, Bundle params, int width, int height) {
        return null;
    }
}
