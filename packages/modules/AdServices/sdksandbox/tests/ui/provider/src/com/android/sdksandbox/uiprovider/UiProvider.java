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

package com.android.sdksandbox.uiprovider;

import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;

public class UiProvider extends SandboxedSdkProvider {

    private boolean mWasClicked = false;

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) throws LoadSdkException {
        return new SandboxedSdk(new UiProviderApiImpl());
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        ImageView view = new ImageView(getContext());
        Drawable drawable = getContext().getDrawable(R.drawable.colors);
        view.setImageDrawable(drawable);
        view.setOnClickListener(
                v -> {
                    mWasClicked = true;
                });
        return view;
    }

    private boolean wasViewClicked() {
        return mWasClicked;
    }

    class UiProviderApiImpl extends IUiProviderApi.Stub {
        @Override
        public boolean wasViewClicked() {
            return UiProvider.this.wasViewClicked();
        }
    }
}
