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
package com.android.telephony.qns.wfc;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.WindowManager;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult;
import androidx.browser.customtabs.CustomTabsClient;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabsServiceConnection;
import androidx.browser.customtabs.CustomTabsSession;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentActivity;

import com.android.internal.annotations.VisibleForTesting;
import com.android.telephony.qns.R;

/** Main activity to handle VoWiFi activation */
public class WfcActivationActivity extends FragmentActivity {

    public static final String TAG = "QNS-WfcActivationActivity";

    private static final String EXTRA_URL = "EXTRA_URL";

    // Message IDs
    private static final int MESSAGE_CHECK_WIFI = 1;
    private static final int MESSAGE_CHECK_WIFI_DONE = 2;
    private static final int MESSAGE_TRY_EPDG_CONNECTION = 3;
    private static final int MESSAGE_TRY_EPDG_CONNECTION_DONE = 4;
    private static final int MESSAGE_SHOW_WEB_PORTAL = 5;

    private WfcActivationHelper mWfcActivationHelper;

    private Handler mUiHandler;
    @VisibleForTesting ProgressDialog mProgressDialog;

    private CustomTabsSession mCustomTabsSession;
    @VisibleForTesting CustomTabsServiceConnection mServiceConnection;
    private ActivityResultLauncher<Intent> mWebviewResultsLauncher =
        registerForActivityResult(
            new StartActivityForResult(),
            activityResult -> {
              if (activityResult.getResultCode() == Activity.RESULT_CANCELED) {
                Log.d(TAG, "Webview Activity Result CANCEL");
                finish();
              } else {
                Log.d(TAG, "Webview Activity Result OK");
                finish();
              }
            });

    // Whether it's safe now to update UI, based on activity visibility.
    // It should be true between onResume() and onPause().
    private boolean mSafeToUpdateUi = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Initialization
        super.onCreate(savedInstanceState);
        createDependencies();
        createUiHandler();

        // Set layout
        setContentView(R.layout.activity_wfc_activation);

        if (WfcUtils.isActivationFlow(getIntent())) {
            // WFC activation flow
            mUiHandler.sendEmptyMessage(MESSAGE_CHECK_WIFI);
        } else {
            // Emergency address update flow
            mUiHandler.sendEmptyMessage(MESSAGE_SHOW_WEB_PORTAL);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSafeToUpdateUi = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mSafeToUpdateUi = false;
    }

    private void createUiHandler() {
        Handler.Callback handlerCallback =
                (Message msg) -> {
                    Log.d(TAG, "UiHandler received: " + msg);
                    switch (msg.what) {
                        case MESSAGE_CHECK_WIFI:
                            mWfcActivationHelper.checkWiFi(
                                    mUiHandler.obtainMessage(MESSAGE_CHECK_WIFI_DONE));
                            break;
                        case MESSAGE_CHECK_WIFI_DONE:
                            if (msg.arg1 == WfcActivationHelper.WIFI_CONNECTION_SUCCESS) {
                                mUiHandler.sendEmptyMessage(MESSAGE_TRY_EPDG_CONNECTION);
                            } else { // msg.arg1 == WfcActivationHelper.WIFI_CONNECTION_ERROR
                                showWiFiUnavailableDialog();
                            }
                            break;
                        case MESSAGE_TRY_EPDG_CONNECTION:
                            showProgressDialog();
                            Log.d(TAG, "Show progress dialog - tryEpdgConnectionOverWiFi");
                            mWfcActivationHelper.tryEpdgConnectionOverWiFi(
                                    mUiHandler.obtainMessage(MESSAGE_TRY_EPDG_CONNECTION_DONE),
                                    mWfcActivationHelper
                                            .getVowifiRegistrationTimerForVowifiActivation());
                            break;
                        case MESSAGE_TRY_EPDG_CONNECTION_DONE:
                            dismissProgressDialog();
                            Log.d(TAG, "Dismiss progress dialog - tryEpdgConnectionOverWiFi");
                            if (msg.arg1 == WfcActivationHelper.EPDG_CONNECTION_SUCCESS) {
                                Log.d(TAG, "VoWiFi activated");
                                setResultAndFinish(RESULT_OK);
                            } else { // msg.arg1 == WfcActivationHelper.EPDG_CONNECTION_ERROR
                                mUiHandler.sendEmptyMessage(MESSAGE_SHOW_WEB_PORTAL);
                            }
                            break;
                        case MESSAGE_SHOW_WEB_PORTAL:
                            startWebPortal();
                            break;
                        default:
                            Log.e(TAG, "UiHandler received unknown message: " + msg);
                            return false;
                    }
                    return true;
                };
        mUiHandler = new Handler(handlerCallback);
    }

    @Override
    public void onDestroy() {
        if (mServiceConnection != null) {
          unbindService(mServiceConnection);
        }
        super.onDestroy();
    }

    private void startWebPortal() {
      Log.d(TAG, "starting web portal ..");
      if (!mSafeToUpdateUi) {
        Log.d(TAG, "Not safe to update UI. Stopping.");
        return;
      }
      String url = mWfcActivationHelper.getWebPortalUrl();
      if (TextUtils.isEmpty(url)) {
        Log.d(TAG, "No web portal url!");
        return;
      }
      if (!mWfcActivationHelper.supportJsCallbackForVowifiPortal()) {
        // For carriers not requiring JS callback in their WFC activation webpage, using a
        // ChromeCustomTab provides richer web functionality while avoiding jumping to the browser
        // app and introducing a discontinuity in UX.
        startCustomTab(url);
      } else {
        // Because QNS uses system UID now, webview cannot be started here. Instead, webview is
        // started in a different activity, {@code R.string.webview_component}.
        startWebPortalActivity();
      }
    }

    private void startCustomTab(String url) {
        mServiceConnection =
            new CustomTabsServiceConnection() {
              @Override
              public void onCustomTabsServiceConnected(
                      ComponentName name, CustomTabsClient client) {
                client.warmup(0L);
                mCustomTabsSession = client.newSession(null);
                mCustomTabsSession.mayLaunchUrl(Uri.parse(url), null, null);
              }

              @Override
              public void onServiceDisconnected(ComponentName name) {
                mCustomTabsSession = null;
              }
            };

        String ServicePackageName =
                getResources().getString(R.string.custom_tabs_service_package_name);
        CustomTabsClient.bindCustomTabsService(this, ServicePackageName, mServiceConnection);
        new CustomTabsIntent.Builder(mCustomTabsSession).build().launchUrl(this, Uri.parse(url));

        if (WfcUtils.isActivationFlow(getIntent())) {
          setResultAndFinish(RESULT_CANCELED);
        } else {
          setResultAndFinish(RESULT_OK);
        }
    }
    private void startWebPortalActivity() {
        String webviewComponent = getResources().getString(R.string.webview_component);
        ComponentName componentName = ComponentName.unflattenFromString(webviewComponent);
        String url = mWfcActivationHelper.getWebPortalUrl();

        Log.d(TAG, "startWebPortalActivity componentName: " + componentName);
        Intent intent = new Intent();
        intent.setComponent(componentName);
        intent.putExtra(EXTRA_URL, url);
        mWebviewResultsLauncher.launch(intent);
    }

    private void showProgressDialog() {
        if (!mSafeToUpdateUi) {
            return;
        }
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            return;
        }
        mProgressDialog =
                new ProgressDialog(
                        new ContextThemeWrapper(
                                this, android.R.style.Theme_DeviceDefault_Light_Dialog));
        mProgressDialog.setCancelable(false);
        mProgressDialog.setCanceledOnTouchOutside(false);
        mProgressDialog.setMessage(getText(R.string.progress_text));
        mProgressDialog.show();
        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    private void dismissProgressDialog() {
        if (!mSafeToUpdateUi) {
            return;
        }
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
            // Allow screen off
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void showWiFiUnavailableDialog() {
        if (!mSafeToUpdateUi) {
            return;
        }
        DialogFragment dialog =
                AlertDialogFragment.newInstance(
                        R.string.connect_to_wifi_or_web_portal_title,
                        R.string.connect_to_wifi_or_web_portal_message);
        dialog.show(getSupportFragmentManager(), "Wifi_unavailable_dialog");
    }

    /** Dialog fragment to show error messages */
    public static class AlertDialogFragment extends DialogFragment {

        private static final String TITLE_KEY = "TITLE_KEY";
        private static final String MESSAGE_KEY = "MESSAGE_KEY";

        /** Static constructor */
        public static AlertDialogFragment newInstance(int titleId, int messageId) {
            AlertDialogFragment frag = new AlertDialogFragment();
            frag.setCancelable(false);

            Bundle args = new Bundle();
            args.putInt(TITLE_KEY, titleId);
            args.putInt(MESSAGE_KEY, messageId);
            frag.setArguments(args);

            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = getArguments();
            int titleId = args.getInt(TITLE_KEY);
            int messageId = args.getInt(MESSAGE_KEY);
            final WfcActivationActivity activity =
                    (WfcActivationActivity) getActivity();
            return new AlertDialog.Builder(
                            new ContextThemeWrapper(
                                    getActivity(),
                                    android.R.style.Theme_DeviceDefault_Light_Dialog))
                    .setTitle(titleId)
                    .setMessage(messageId)
                    .setPositiveButton(
                            R.string.button_setup_web_portal,
                            (OnClickListener)
                                    (dialog, which) ->
                                            activity.mUiHandler.sendEmptyMessage(
                                                    MESSAGE_SHOW_WEB_PORTAL))
                    .setNegativeButton(
                            R.string.button_turn_on_wifi,
                            (OnClickListener)
                                    (dialog, which) -> {
                                        // Redirect to WiFi settings UI
                                        Intent intent = new Intent(Settings.ACTION_WIFI_SETTINGS);
                                        activity.startActivity(intent);
                                        // And finish self
                                        activity.setResultAndFinish(RESULT_CANCELED);
                                    })
                    .create();
        }
    }

    private void setResultAndFinish(int resultCode) {
        setResult(resultCode);
        finish();
    }

    private void createDependencies() {
        // Default initialization for production
        int subId = WfcUtils.getSubId(getIntent());

        if (WfcUtils.getWfcActivationHelper() != null) {
            mWfcActivationHelper = WfcUtils.getWfcActivationHelper();
          Log.v(TAG, "WfcActivationHelper injected: " + mWfcActivationHelper);
        } else {
            mWfcActivationHelper = new WfcActivationHelper(this, subId);
        }

        if (WfcUtils.getWebviewResultLauncher() != null) {
            mWebviewResultsLauncher = WfcUtils.getWebviewResultLauncher();
            Log.v(TAG, "getWebviewResultLauncher injected: " + mWebviewResultsLauncher);
        }
    }
}
