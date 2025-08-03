/*
 * Copyright 2023 The Android Open Source Project
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
package android.view.inputmethod.ctstestlauncher;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.cts.util.MockTestActivityUtil;

/**
 * A test launcher activity which display a full screen handwriting delegator view. Handwriting on
 * the view triggers the intent to open android.view.inputmethod.ctstestapp.MainActivity and start
 * handwriting in the EditText in that activity.
 */
public final class LauncherActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Intent to launch the ctstestapp MainActivity which contains a delegate editor.
        Intent ctsTestAppIntent = new Intent(Intent.ACTION_MAIN);
        ctsTestAppIntent.setComponent(new ComponentName(
                "android.view.inputmethod.ctstestapp",
                "android.view.inputmethod.ctstestapp.MainActivity"));
        ctsTestAppIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        String privateImeOptions =
                getIntent().getStringExtra(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS);
        if (privateImeOptions != null) {
            ctsTestAppIntent.putExtra(
                    MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS, privateImeOptions);
        }
        if (getIntent().getBooleanExtra(MockTestActivityUtil.EXTRA_HANDWRITING_DELEGATE, false)) {
            ctsTestAppIntent.putExtra(MockTestActivityUtil.EXTRA_HANDWRITING_DELEGATE, true);
        }
        if (getIntent().getBooleanExtra(
                MockTestActivityUtil.EXTRA_HOME_HANDWRITING_DELEGATOR_ALLOWED, false)) {
            ctsTestAppIntent.putExtra(
                    MockTestActivityUtil.EXTRA_HOME_HANDWRITING_DELEGATOR_ALLOWED, true);
        }

        // Full screen handwriting delegator view
        View delegatorView = new View(this);
        delegatorView.setBackgroundColor(Color.GREEN);
        delegatorView.setHandwritingDelegatorCallback(() -> startActivity(ctsTestAppIntent));
        delegatorView.setAllowedHandwritingDelegatePackage("android.view.inputmethod.ctstestapp");
        setContentView(delegatorView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }
}
