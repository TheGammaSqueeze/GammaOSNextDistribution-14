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

package com.android.sdksandboxwebviewclient;

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_SURFACE_PACKAGE;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.RequestSurfacePackageException;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.interfaces.IWebViewSdkApi;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.OutcomeReceiver;
import android.os.RemoteException;
import android.util.Log;
import android.view.SurfaceControlViewHost.SurfacePackage;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final String SDK_NAME = "com.android.sdksandboxcode_webview";
    private static final String TAG = "SdkSandboxWebViewClientMainActivity";
    private static final Handler sHandler = new Handler(Looper.getMainLooper());

    private boolean mSdksLoaded = false;

    private SdkSandboxManager mSdkSandboxManager;
    private Button mLoadButton;
    private Button mRenderButton;
    private Button mSubmitUrlButton;
    private EditText mUrlInputTextbox;
    private LinearLayout mUrlInputField;
    private SurfaceView mRenderedView;
    private SandboxedSdk mSandboxedSdk;
    private IWebViewSdkApi mWebViewProxy;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSdkSandboxManager = getApplicationContext().getSystemService(SdkSandboxManager.class);

        mRenderedView = findViewById(R.id.rendered_view);
        mRenderedView.setZOrderOnTop(true);
        mRenderedView.setVisibility(View.INVISIBLE);

        mLoadButton = findViewById(R.id.load_sdk_button);
        mRenderButton = findViewById(R.id.request_surface_button);
        mUrlInputTextbox = findViewById(R.id.url_input_textbox);
        mSubmitUrlButton = findViewById(R.id.submit_url_button);
        mUrlInputField = findViewById(R.id.url_input_field);

        mUrlInputField.setVisibility(View.GONE);

        registerLoadSdkProviderButton();
        registerLoadSurfacePackageButton();
        registerSubmitUrlButton();
    }

    private void registerLoadSdkProviderButton() {
        mLoadButton.setOnClickListener(
                v -> {
                    if (mSdksLoaded) {
                        resetStateForLoadSdkButton();
                        return;
                    }
                    // Register for sandbox death event.
                    mSdkSandboxManager.addSdkSandboxProcessDeathCallback(
                            Runnable::run, () -> makeToast("Sdk Sandbox process died"));

                    Bundle params = new Bundle();
                    OutcomeReceiver<SandboxedSdk, LoadSdkException> receiver =
                            new OutcomeReceiver<SandboxedSdk, LoadSdkException>() {
                                @Override
                                public void onResult(SandboxedSdk sandboxedSdk) {
                                    mSdksLoaded = true;
                                    mSandboxedSdk = sandboxedSdk;
                                    mWebViewProxy =
                                            IWebViewSdkApi.Stub.asInterface(
                                                    mSandboxedSdk.getInterface());
                                    makeToast("SDK Loaded successfully!");
                                    mLoadButton.setText(
                                            getResources().getString(R.string.unload_sdk));
                                }

                                @Override
                                public void onError(LoadSdkException error) {
                                    makeToast("Failed: " + error);
                                    Log.e(TAG, error.getMessage(), error);
                                }
                            };
                    mSdkSandboxManager.loadSdk(SDK_NAME, params, Runnable::run, receiver);
                });
    }

    private void resetStateForLoadSdkButton() {
        try {
            mWebViewProxy.destroy();
        } catch (RemoteException e) {
            Log.e(TAG, e.getMessage());
        }
        mSdkSandboxManager.unloadSdk(SDK_NAME);
        mRenderedView.setVisibility(View.INVISIBLE);
        mLoadButton.setText(getResources().getString(R.string.load_sdk));
        mUrlInputField.setVisibility(View.GONE);
        mSdksLoaded = false;
    }

    private void registerLoadSurfacePackageButton() {
        OutcomeReceiver<Bundle, RequestSurfacePackageException> receiver =
                new RequestSurfacePackageReceiver();
        mRenderButton.setOnClickListener(
                v -> {
                    if (mSdksLoaded) {
                        sHandler.post(
                                () -> {
                                    mSdkSandboxManager.requestSurfacePackage(
                                            SDK_NAME,
                                            getRequestSurfacePackageParams(),
                                            Runnable::run,
                                            receiver);
                                });
                    } else {
                        makeToast("Sdk is not loaded");
                    }
                });
    }

    private void registerSubmitUrlButton() {
        mSubmitUrlButton.setOnClickListener(
                v -> {
                    String url = mUrlInputTextbox.getText().toString();
                    try {
                        mWebViewProxy.loadUrl(url);
                    } catch (RemoteException e) {
                        Log.e(TAG, e.getMessage());
                        makeToast("Cannot load URL : " + e.getMessage());
                    }
                });
    }

    private Bundle getRequestSurfacePackageParams() {
        Bundle params = new Bundle();
        params.putInt(EXTRA_WIDTH_IN_PIXELS, mRenderedView.getWidth());
        params.putInt(EXTRA_HEIGHT_IN_PIXELS, mRenderedView.getHeight());
        params.putInt(EXTRA_DISPLAY_ID, getDisplay().getDisplayId());
        params.putBinder(EXTRA_HOST_TOKEN, mRenderedView.getHostToken());
        return params;
    }

    private void makeToast(String message) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, message, Toast.LENGTH_LONG).show());
    }

    private class RequestSurfacePackageReceiver
            implements OutcomeReceiver<Bundle, RequestSurfacePackageException> {

        @Override
        public void onResult(Bundle result) {
            mUrlInputField.setVisibility(View.VISIBLE);
            sHandler.post(
                    () -> {
                        SurfacePackage surfacePackage =
                                result.getParcelable(EXTRA_SURFACE_PACKAGE, SurfacePackage.class);
                        mRenderedView.setChildSurfacePackage(surfacePackage);
                        mRenderedView.setVisibility(View.VISIBLE);
                    });
            makeToast("Rendered WebView");
        }

        @Override
        public void onError(@NonNull RequestSurfacePackageException error) {
            makeToast("Failed: " + error.getMessage());
            Log.e(TAG, error.getMessage(), error);
        }
    }
}
