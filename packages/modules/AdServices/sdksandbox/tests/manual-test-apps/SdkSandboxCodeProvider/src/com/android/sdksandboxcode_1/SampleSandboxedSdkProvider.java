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

package com.android.sdksandboxcode_1;

import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SandboxedSdkProvider;
import android.app.sdksandbox.interfaces.IMediateeSdkApi;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.EditText;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Random;

public class SampleSandboxedSdkProvider extends SandboxedSdkProvider {

    private static final String TAG = "SampleSandboxedSdkProvider";

    private static final String VIEW_TYPE_KEY = "view-type";
    private static final String VIDEO_VIEW_VALUE = "video-view";
    private static final String VIEW_TYPE_INFLATED_VIEW = "view-type-inflated-view";
    private static final String VIEW_TYPE_WEBVIEW = "view-type-webview";
    private static final String VIEW_TYPE_AD_REFRESH = "view-type-ad-refresh";
    private static final String VIEW_TYPE_EDITTEXT = "view-type-edittext";
    private static final String VIDEO_URL_KEY = "video-url";
    private static final String EXTRA_SDK_SDK_ENABLED_KEY = "sdkSdkCommEnabled";
    private static final String APP_OWNED_SDK_NAME = "app-sdk-1";
    private static final String ON_CLICK_BEHAVIOUR_TYPE_KEY = "on-click-behavior";
    private static final String ON_CLICK_OPEN_CHROME = "on-click-open-chrome";
    private static final String ON_CLICK_OPEN_PACKAGE = "on-click-open-package";
    private static final String PACKAGE_TO_OPEN_KEY = "package-to-open";
    private final PlayerViewProvider mPlayerViewProvider = new PlayerViewProvider();
    private String mSdkSdkCommEnabled = null;
    private String mOnClickOpenPackage = null;

    @Override
    public SandboxedSdk onLoadSdk(Bundle params) {
        return new SandboxedSdk(new SdkApi(getContext(), mPlayerViewProvider));
    }

    @Override
    public void beforeUnloadSdk() {
        mPlayerViewProvider.onHostActivityStopped();
        Log.i(TAG, "SDK unloaded");
    }

    @Override
    public View getView(Context windowContext, Bundle params, int width, int height) {
        if (params.getString(ON_CLICK_BEHAVIOUR_TYPE_KEY, "").equals(ON_CLICK_OPEN_PACKAGE)) {
            mOnClickOpenPackage = params.getString(PACKAGE_TO_OPEN_KEY, "");
        }
        String type = params.getString(VIEW_TYPE_KEY, "");
        if (VIDEO_VIEW_VALUE.equals(type)) {
            String videoUrl = params.getString(VIDEO_URL_KEY, "");
            return mPlayerViewProvider.createPlayerView(windowContext, videoUrl);
        } else if (VIEW_TYPE_INFLATED_VIEW.equals(type)) {
            final LayoutInflater inflater =
                    (LayoutInflater)
                            windowContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.sample_layout, null);
            view.setOnClickListener(getOnClickListener(getContext()));
            return view;
        } else if (VIEW_TYPE_WEBVIEW.equals(type)) {
            return new TestWebView(windowContext);
        } else if (VIEW_TYPE_AD_REFRESH.equals(type)) {
            final LayoutInflater inflater =
                    (LayoutInflater)
                            windowContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            final View view = inflater.inflate(R.layout.sample_layout, null);
            view.setOnClickListener(getOnClickListener(getContext()));
            view.postDelayed(
                    () -> {
                        final Random random = new Random();
                        final TextView textView = (TextView) view.findViewById(R.id.textViewItem);
                        textView.setBackgroundColor(
                                Color.rgb(
                                        random.nextInt(256),
                                        random.nextInt(256),
                                        random.nextInt(256)));

                        view.postDelayed(
                                () -> {
                                    final RelativeLayout rl =
                                            (RelativeLayout) view.findViewById(R.id.item);
                                    rl.setBackgroundColor(
                                            Color.rgb(
                                                    random.nextInt(256),
                                                    random.nextInt(256),
                                                    random.nextInt(256)));
                                },
                                2000);
                    },
                    1000);

            return view;
        } else if (VIEW_TYPE_EDITTEXT.equals(type)) {
            EditText editText = new EditText(windowContext);
            editText.setWidth(width);
            editText.setHeight(height);
            editText.setBackgroundColor(Color.BLUE);
            editText.setTextColor(Color.WHITE);
            editText.setText("Enter text: ");
            return editText;
        }
        mSdkSdkCommEnabled = params.getString(EXTRA_SDK_SDK_ENABLED_KEY, null);

        return new TestView(
                windowContext,
                getContext(),
                mSdkSdkCommEnabled,
                getOnClickListener(getContext()),
                width);
    }

    protected View.OnClickListener getOnClickListener(Context context) {
        if (mOnClickOpenPackage == null) {
            return new OpenBrowserOnClickListener(context);
        } else return new OpenPackageOnClickListener(context, mOnClickOpenPackage);
    }

    private static class TestView extends View {

        private static final CharSequence MEDIATEE_SDK = "com.android.sdksandboxcode_mediatee";
        private static final String DROPDOWN_KEY_SDK_SANDBOX = "SDK_IN_SANDBOX";
        private static final String DROPDOWN_KEY_SDK_APP = "SDK_IN_APP";
        private Context mSdkContext;
        private String mSdkToSdkCommEnabled;
        private View.OnClickListener mClickListener;
        private int mWidth;

        TestView(
                Context windowContext,
                Context sdkContext,
                String sdkSdkCommEnabled,
                View.OnClickListener clickListener,
                int width) {
            super(windowContext);
            mSdkContext = sdkContext;
            mSdkToSdkCommEnabled = sdkSdkCommEnabled;
            mClickListener = clickListener;
            mWidth = width;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            Random random = new Random();
            String message = null;

            if (!TextUtils.isEmpty(mSdkToSdkCommEnabled)) {
                if (mSdkToSdkCommEnabled.equals(DROPDOWN_KEY_SDK_SANDBOX)) {
                    SandboxedSdk mediateeSdk;
                    try {
                        // get message from another sandboxed SDK
                        List<SandboxedSdk> sandboxedSdks =
                                mSdkContext
                                        .getSystemService(SdkSandboxController.class)
                                        .getSandboxedSdks();
                        mediateeSdk =
                                sandboxedSdks.stream()
                                        .filter(
                                                s ->
                                                        s.getSharedLibraryInfo()
                                                                .getName()
                                                                .contains(MEDIATEE_SDK))
                                        .findAny()
                                        .get();
                    } catch (Exception e) {
                        throw new IllegalStateException("Error in sdk-sdk communication ", e);
                    }
                    try {
                        IBinder binder = mediateeSdk.getInterface();
                        IMediateeSdkApi sdkApi = IMediateeSdkApi.Stub.asInterface(binder);
                        message = sdkApi.getMessage();
                    } catch (RemoteException e) {
                        throw new IllegalStateException(e);
                    }
                } else if (mSdkToSdkCommEnabled.equals(DROPDOWN_KEY_SDK_APP)) {
                    try {
                        // get message from an app owned SDK
                        List<AppOwnedSdkSandboxInterface> appOwnedSdks =
                                mSdkContext
                                        .getSystemService(SdkSandboxController.class)
                                        .getAppOwnedSdkSandboxInterfaces();

                        AppOwnedSdkSandboxInterface appOwnedSdk =
                                appOwnedSdks.stream()
                                        .filter(s -> s.getName().contains(APP_OWNED_SDK_NAME))
                                        .findAny()
                                        .get();
                        IMediateeSdkApi appOwnedSdkApi =
                                IMediateeSdkApi.Stub.asInterface(appOwnedSdk.getInterface());
                        message = appOwnedSdkApi.getMessage();
                    } catch (RemoteException e) {
                        throw new IllegalStateException(e);
                    }
                }
            } else {
                message = mSdkContext.getResources().getString(R.string.view_message);
            }
            int c = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256));
            canvas.drawColor(c);

            TextPaint paint = new TextPaint();
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.WHITE);
            paint.setTextSize(50);
            StaticLayout.Builder.obtain(message, 0, message.length(), paint, mWidth)
                    .build()
                    .draw(canvas);

            setOnClickListener(mClickListener);
        }
    }

    private static class OpenBrowserOnClickListener implements View.OnClickListener {

        private final Context mSdkContext;

        private OpenBrowserOnClickListener(Context sdkContext) {
            mSdkContext = sdkContext;
        }

        @Override
        public void onClick(View view) {
            Context context = view.getContext();
            Toast.makeText(context, "Opening url", Toast.LENGTH_LONG).show();

            String url = "http://www.google.com";
            Intent visitUrl = new Intent(Intent.ACTION_VIEW);
            visitUrl.setData(Uri.parse(url));
            visitUrl.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            mSdkContext.startActivity(visitUrl);
        }

    }

    private static class OpenPackageOnClickListener implements View.OnClickListener {

        private final Context mSdkContext;
        private final String mPackageToOpen;

        private OpenPackageOnClickListener(Context sdkContext, String packageToOpen) {
            mSdkContext = sdkContext;
            mPackageToOpen = packageToOpen;
        }

        @Override
        public void onClick(View view) {
            Context context = view.getContext();
            Intent intent =
                    mSdkContext.getPackageManager().getLaunchIntentForPackage(mPackageToOpen);
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mSdkContext.startActivity(intent);
            } else {
                Log.i(TAG, "Opening the Play store.");
                intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(
                        Uri.parse(
                                "https://play.google.com/store/apps/details?id=" + mPackageToOpen));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mSdkContext.startActivity(intent);
            }
        }
    }

    private static class TestWebView extends WebView {
        TestWebView(Context windowContext) {
            super(windowContext);
            initializeSettings(getSettings());
            loadUrl("https://www.google.com/");
        }

        private void initializeSettings(WebSettings settings) {
            settings.setJavaScriptEnabled(true);
            settings.setGeolocationEnabled(true);
            settings.setSupportZoom(true);
            settings.setDatabaseEnabled(true);
            settings.setDomStorageEnabled(true);
            settings.setAllowFileAccess(true);
            settings.setAllowContentAccess(true);
            settings.setUseWideViewPort(true);
            settings.setLoadWithOverviewMode(true);
            settings.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.TEXT_AUTOSIZING);
        }
    }
}
