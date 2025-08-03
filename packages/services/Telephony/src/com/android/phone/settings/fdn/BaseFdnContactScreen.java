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

package com.android.phone.settings.fdn;

import static android.view.Window.PROGRESS_VISIBILITY_OFF;
import static android.view.Window.PROGRESS_VISIBILITY_ON;

import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Window;
import android.widget.Toast;

import com.android.internal.telephony.CommandException;
import com.android.internal.telephony.Phone;
import com.android.phone.PhoneGlobals;
import com.android.phone.R;
import com.android.phone.SubscriptionInfoHelper;

/**
 * Base activity for FDN contact screen.
 */
public abstract class BaseFdnContactScreen extends Activity
        implements Pin2LockedDialogFragment.Listener {
    protected static final String LOG_TAG = PhoneGlobals.LOG_TAG;
    protected static final boolean DBG = false;

    protected static final int EVENT_PIN2_ENTRY_COMPLETE = 10;
    protected static final int PIN2_REQUEST_CODE = 100;

    protected static final String INTENT_EXTRA_NAME = "name";
    protected static final String INTENT_EXTRA_NUMBER = "number";

    protected String mName;
    protected String mNumber;
    protected String mPin2;

    protected SubscriptionInfoHelper mSubscriptionInfoHelper;
    protected BaseFdnContactScreen.QueryHandler mQueryHandler;

    protected Handler mHandler = new Handler();
    protected Phone mPhone;

    protected abstract void pin2AuthenticationSucceed();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        resolveIntent();
        getWindow().requestFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        getWindow().addSystemFlags(
                android.view.WindowManager.LayoutParams
                        .SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
    }

    protected void authenticatePin2() {
        Intent intent = new Intent();
        intent.setClass(this, GetPin2Screen.class);
        intent.setData(FdnList.getContentUri(mSubscriptionInfoHelper));
        startActivityForResult(intent, PIN2_REQUEST_CODE);
    }

    protected void displayProgress(boolean flag) {
        getWindow().setFeatureInt(
                Window.FEATURE_INDETERMINATE_PROGRESS,
                flag ? PROGRESS_VISIBILITY_ON : PROGRESS_VISIBILITY_OFF);
    }

    protected void handleResult(boolean success) {
    }

    protected void handleResult(boolean success, boolean invalidNumber) {
    }

    protected void log(String msg) {
        Log.d(LOG_TAG, getClass().getSimpleName() + " : " + msg);
    }

    // Add method to check if Pin2 supplied is correct.
    protected void processPin2(String pin) {
        Message onComplete = mFDNHandler
                .obtainMessage(EVENT_PIN2_ENTRY_COMPLETE);
        mPhone.getIccCard().supplyPin2(pin, onComplete);
    }

    protected void resolveIntent() {
        Intent intent = getIntent();

        mSubscriptionInfoHelper = new SubscriptionInfoHelper(this, intent);
        mPhone = mSubscriptionInfoHelper.getPhone();

        mName =  intent.getStringExtra(INTENT_EXTRA_NAME);
        mNumber =  intent.getStringExtra(INTENT_EXTRA_NUMBER);
    }

    /**
     * Removed the status field, with preference to displaying a toast
     * to match the rest of settings UI.
     */
    protected void showStatus(CharSequence statusMsg) {
        if (statusMsg != null) {
            Toast.makeText(this, statusMsg, Toast.LENGTH_LONG).show();
        }
    }

    private Handler mFDNHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case EVENT_PIN2_ENTRY_COMPLETE:
                    AsyncResult ar = (AsyncResult) msg.obj;
                    if (ar.exception != null) {
                        // see if PUK2 is requested and alert the user accordingly.
                        CommandException ce = (CommandException) ar.exception;
                        if (ce.getCommandError() == CommandException.Error.SIM_PUK2) {
                            // make sure we set the PUK2 state so that we can skip some
                            // redundant behaviour.
                            showPin2LockedDialog();
                        } else {
                            final int attemptsRemaining = msg.arg1;
                            if (attemptsRemaining > 0) {
                                Toast.makeText(
                                        BaseFdnContactScreen.this,
                                        getString(R.string.pin2_invalid)
                                                + getString(R.string.pin2_attempts,
                                                        attemptsRemaining), Toast.LENGTH_LONG)
                                        .show();
                                finish();
                            }
                        }
                    } else {
                        pin2AuthenticationSucceed();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    protected class QueryHandler extends AsyncQueryHandler {
        protected QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
            if (DBG) log("onInsertComplete");
            displayProgress(false);
            handleResult(uri != null, false);
        }

        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            if (DBG) log("onUpdateComplete");
            displayProgress(false);
            handleResult(result > 0, false);
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            if (DBG) log("onDeleteComplete");
            displayProgress(false);
            handleResult(result > 0);
        }
    }

    private void showPin2LockedDialog() {
        final FragmentManager fragmentManager = getFragmentManager();
        Pin2LockedDialogFragment dialogFragment = (Pin2LockedDialogFragment) fragmentManager
                .findFragmentByTag(Pin2LockedDialogFragment.TAG_PIN2_LOCKED_DIALOG);
        if (dialogFragment == null) {
            dialogFragment = new Pin2LockedDialogFragment();
            Bundle args = new Bundle();
            args.putInt(Pin2LockedDialogFragment.KEY_DIALOG_ID,
                    Pin2LockedDialogFragment.DIALOG_ID_PUK2_REQUESTED_ON_PIN_ENTRY);
            dialogFragment.setArguments(args);
            dialogFragment.show(fragmentManager, Pin2LockedDialogFragment.TAG_PIN2_LOCKED_DIALOG);
        } else {
            FragmentTransaction transaction = fragmentManager.beginTransaction();
            transaction.show(dialogFragment);
            transaction.commitNow();
        }
    }

    @Override
    public void onRequestPuk2(int id) {
        finish();
    }
}
