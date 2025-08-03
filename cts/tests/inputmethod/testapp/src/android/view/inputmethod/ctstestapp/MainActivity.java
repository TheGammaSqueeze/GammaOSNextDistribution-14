/*
 * Copyright (C) 2020 The Android Open Source Project
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
package android.view.inputmethod.ctstestapp;

import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN;
import static android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteCallback;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.cts.util.MockTestActivityUtil;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

/**
 * A test {@link Activity} that automatically shows the input method.
 */
public final class MainActivity extends Activity {

    private AlertDialog mDialog;
    private EditText mEditor;
    private final Handler mHandler = new Handler(Looper.myLooper());

    private BroadcastReceiver mBroadcastReceiver;

    @Nullable
    private String getStringIntentExtra(String key) {
        if (getPackageManager().isInstantApp()) {
            final Uri uri = getIntent().getData();
            if (uri == null || !uri.isHierarchical()) {
                return null;
            }
            return uri.getQueryParameter(key);
        }
        return getIntent().getStringExtra(key);
    }

    private boolean getBooleanIntentExtra(String key) {
        if (getPackageManager().isInstantApp()) {
            final Uri uri = getIntent().getData();
            if (uri == null || !uri.isHierarchical()) {
                return false;
            }
            return uri.getBooleanQueryParameter(key, false);
        }
        return getIntent().getBooleanExtra(key, false) || "true".equals(
                getIntent().getStringExtra(key));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        final boolean needShowDialog =
                getBooleanIntentExtra(MockTestActivityUtil.EXTRA_KEY_SHOW_DIALOG);

        if (needShowDialog) {
            layout.setOrientation(LinearLayout.VERTICAL);
            layout.setGravity(Gravity.BOTTOM);
            getWindow().setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE);

            final TextView textView = new TextView(this);
            textView.setText("This is DialogActivity");
            layout.addView(textView);

            mDialog = new AlertDialog.Builder(this).setView(new LinearLayout(this)).create();
            mDialog.getWindow().addFlags(FLAG_ALT_FOCUSABLE_IM);
            mDialog.getWindow().setSoftInputMode(SOFT_INPUT_ADJUST_PAN);
            mDialog.show();
        } else {
            RemoteCallback remoteCallback = getIntent().getParcelableExtra(
                    MockTestActivityUtil.EXTRA_ON_CREATE_INPUT_CONNECTION_CALLBACK,
                    RemoteCallback.class);
            String sessionId = getIntent().getStringExtra(
                    MockTestActivityUtil.EXTRA_ON_CREATE_USER_HANDLE_SESSION_ID);
            if (remoteCallback == null || sessionId == null) {
                mEditor = new EditText(this);
            } else {
                mEditor = new EditText(this) {
                    @Override
                    public InputConnection onCreateInputConnection(EditorInfo editorInfo) {
                        final InputConnection original = super.onCreateInputConnection(editorInfo);
                        if (editorInfo.extras == null) {
                            editorInfo.extras = new Bundle();
                        }
                        editorInfo.extras.putString(
                                ReplyReceivingInputConnection
                                        .EDITOR_INFO_KEY_REPLY_USER_HANDLE_SESSION_ID,
                                sessionId);
                        return new ReplyReceivingInputConnection(original, sessionId,
                                remoteCallback);
                    }
                };
            }
            mEditor.setHint("editText");
            final String privateImeOptions =
                    getStringIntentExtra(MockTestActivityUtil.EXTRA_KEY_PRIVATE_IME_OPTIONS);
            if (privateImeOptions != null) {
                mEditor.setPrivateImeOptions(privateImeOptions);
            }
            if (getBooleanIntentExtra(MockTestActivityUtil.EXTRA_HANDWRITING_DELEGATE)) {
                mEditor.setIsHandwritingDelegate(true);
                mEditor.setAllowedHandwritingDelegatorPackage("android.view.inputmethod.cts");
            }
            if (getBooleanIntentExtra(
                    MockTestActivityUtil.EXTRA_HOME_HANDWRITING_DELEGATOR_ALLOWED)) {
                mEditor.setHandwritingDelegateFlags(
                        InputMethodManager.HANDWRITING_DELEGATE_FLAG_HOME_DELEGATOR_ALLOWED);
            }
            mEditor.requestFocus();
            layout.addView(mEditor);
        }

        setContentView(layout);
    }

    @Override
    protected void onStart() {
        super.onStart();
        mBroadcastReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                final Bundle extras = intent.getExtras();
                if (extras == null) {
                    return;
                }

                if (extras.containsKey(MockTestActivityUtil.EXTRA_SHOW_SOFT_INPUT)) {
                    getSystemService(InputMethodManager.class).showSoftInput(mEditor, 0);
                }

                if (extras.getBoolean(MockTestActivityUtil.EXTRA_DISMISS_DIALOG, false)) {
                    if (mDialog != null) {
                        mDialog.dismiss();
                        mDialog = null;
                    }
                    mHandler.postDelayed(() -> finish(), 100);
                }
            }
        };
        registerReceiver(mBroadcastReceiver, new IntentFilter(MockTestActivityUtil.ACTION_TRIGGER),
                Context.RECEIVER_EXPORTED);
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mBroadcastReceiver != null) {
            unregisterReceiver(mBroadcastReceiver);
            mBroadcastReceiver = null;
        }
    }
}
