/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.notifications;

import static android.provider.Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS;
import static android.provider.Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME;
import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.provider.Settings.Secure;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.android.cts.verifier.PassFailButtons;
import com.android.cts.verifier.R;
import com.android.cts.verifier.TestListActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedBlockingQueue;

public abstract class InteractiveVerifierActivity extends PassFailButtons.Activity
        implements Runnable {
    private static final String TAG = "InteractiveVerifier";
    private static final String STATE = "state";
    private static final String STATUS = "status";
    private static final String SCROLLY = "scrolly";
    private static final String DISPLAY_MODE = "display_mode";
    private static LinkedBlockingQueue<String> sDeletedQueue = new LinkedBlockingQueue<String>();
    protected static final String LISTENER_PATH = "com.android.cts.verifier/" +
            "com.android.cts.verifier.notifications.MockListener";
    protected static final int SETUP = 0;
    protected static final int READY = 1;
    protected static final int RETEST = 2;
    protected static final int PASS = 3;
    protected static final int FAIL = 4;
    protected static final int WAIT_FOR_USER = 5;
    protected static final int RETEST_AFTER_LONG_DELAY = 6;
    protected static final int READY_AFTER_LONG_DELAY = 7;

    protected static final int NOTIFICATION_ID = 1001;

    // TODO remove these once b/10023397 is fixed
    public static final String ENABLED_NOTIFICATION_LISTENERS = "enabled_notification_listeners";

    protected InteractiveTestCase mCurrentTest;
    protected PackageManager mPackageManager;
    protected NotificationManager mNm;
    protected Context mContext;
    protected Runnable mRunner;
    protected View mHandler;
    protected String mPackageString;

    private LayoutInflater mInflater;
    private LinearLayout mItemList;
    private ScrollView mScrollView;
    private List<InteractiveTestCase> mTestList;
    private Iterator<InteractiveTestCase> mTestOrder;

    public static class DismissService extends Service {
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }

        @Override
        public void onStart(Intent intent, int startId) {
            if(intent != null) { sDeletedQueue.offer(intent.getAction()); }
        }
    }

    protected abstract class InteractiveTestCase {
        protected boolean mUserVerified;
        protected int status;
        private View view;
        protected long delayTime = 3000;
        boolean buttonPressed;

        protected abstract View inflate(ViewGroup parent);
        View getView(ViewGroup parent) {
            if (view == null) {
                view = inflate(parent);
            }
            view.setTag(this.getClass().getSimpleName());
            return view;
        }

        /** @return true if the test should re-run when the test activity starts. */
        boolean autoStart() {
            return false;
        }

        /** @return the test's status after autostart. */
        int autoStartStatus() {
            return READY;
        }

        /** Set status to {@link #READY} to proceed, or {@link #SETUP} to try again. */
        protected void setUp() { status = READY; next(); };

        /** Set status to {@link #PASS} or @{link #FAIL} to proceed, or {@link #READY} to retry. */
        protected void test() { status = FAIL; next(); };

        /** Do not modify status. */
        protected void tearDown() { next(); };

        protected void setFailed() {
            status = FAIL;
            logFail();
        }

        protected void logFail() {
            logFail(null);
        }

        protected void logFail(String message) {
            logWithStack("failed " + this.getClass().getSimpleName() +
                    ((message == null) ? "" : ": " + message));
        }

        protected void logFail(String message, Throwable e) {
            Log.e(TAG, "failed " + this.getClass().getSimpleName() +
                    ((message == null) ? "" : ": " + message), e);
        }

        // If this test contains a button that launches another activity, override this
        // method to provide the intent to launch.
        protected Intent getIntent() {
            return null;
        }
    }

    protected abstract int getTitleResource();
    protected abstract int getInstructionsResource();

    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        int savedStateIndex = (savedState == null) ? 0 : savedState.getInt(STATE, 0);
        int savedStatus = (savedState == null) ? SETUP : savedState.getInt(STATUS, SETUP);
        int scrollY = (savedState == null) ? 0 : savedState.getInt(SCROLLY, 0);
        String displayMode = (savedState == null) ? null : savedState.getString(DISPLAY_MODE, null);
        if (displayMode != null) {
            TestListActivity.sCurrentDisplayMode = displayMode;
        }
        Log.i(TAG, "restored state(" + savedStateIndex + "}, status(" + savedStatus + ")");
        mContext = this;
        mRunner = this;
        mNm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mPackageManager = getPackageManager();
        mInflater = getLayoutInflater();
        View view = mInflater.inflate(R.layout.nls_main, null);
        mScrollView = view.findViewById(R.id.nls_test_scroller);
        mItemList = view.findViewById(R.id.nls_test_items);
        mHandler = mItemList;
        mTestList = new ArrayList<>();
        mTestList.addAll(createTestItems());

        if (!mTestList.isEmpty()) {
            setupTests(savedStateIndex, savedStatus, scrollY);
            view.findViewById(R.id.pass_button).setEnabled(false);
        } else {
            view.findViewById(R.id.empty_text).setVisibility(VISIBLE);
            view.findViewById(R.id.fail_button).setEnabled(false);
        }

        setContentView(view);
        setPassFailButtonClickListeners();
        setInfoResources(getTitleResource(), getInstructionsResource(), -1);
    }

    private void setupTests(int savedStateIndex, int savedStatus, int scrollY) {
        for (InteractiveTestCase test : mTestList) {
            mItemList.addView(test.getView(mItemList));
        }
        mTestOrder = mTestList.iterator();
        for (int i = 0; i < savedStateIndex; i++) {
            mCurrentTest = mTestOrder.next();
            mCurrentTest.status = PASS;
            markItem(mCurrentTest);
        }

        mCurrentTest = mTestOrder.next();
        mCurrentTest.status = savedStatus;

        mScrollView.post(() -> mScrollView.smoothScrollTo(0, scrollY));
    }

    @Override
    protected void onSaveInstanceState (Bundle outState) {
        final int stateIndex = mTestList.indexOf(mCurrentTest);
        outState.putInt(STATE, stateIndex);
        final int status = mCurrentTest == null ? SETUP : mCurrentTest.status;
        outState.putInt(STATUS, status);
        outState.putInt(SCROLLY, mScrollView.getScrollY());
        outState.putString(DISPLAY_MODE, TestListActivity.sCurrentDisplayMode);
        Log.i(TAG, "saved state(" + stateIndex + "), status(" + status + ")");
    }

    @Override
    protected void onResume() {
        super.onResume();
        //To avoid NPE during onResume,before start to iterate next test order
        if (mCurrentTest != null && mCurrentTest.status != SETUP && mCurrentTest.autoStart()) {
            Log.i(TAG, "auto starting: " + mCurrentTest.getClass().getSimpleName());
            mCurrentTest.status = mCurrentTest.autoStartStatus();
        }
        next();
    }

    // Interface Utilities

    protected final void setButtonsEnabled(View view, boolean enabled) {
        if (view instanceof Button) {
            view.setEnabled(enabled);
        } else if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);
                setButtonsEnabled(child, enabled);
            }
        }
    }

    protected void markItem(InteractiveTestCase test) {
        if (test == null) { return; }
        View item = test.view;
        ImageView status = item.findViewById(R.id.nls_status);
        switch (test.status) {
            case WAIT_FOR_USER:
                status.setImageResource(R.drawable.fs_warning);
                break;

            case SETUP:
            case READY:
            case RETEST:
                status.setImageResource(R.drawable.fs_clock);
                break;

            case FAIL:
                status.setImageResource(R.drawable.fs_error);
                setButtonsEnabled(test.view, false);
                break;

            case PASS:
                status.setImageResource(R.drawable.fs_good);
                setButtonsEnabled(test.view, false);
                break;

        }
        status.invalidate();
    }

    protected View createNlsSettingsItem(ViewGroup parent, int messageId) {
        return createUserItem(parent, R.string.nls_start_settings, messageId);
    }

    protected View createRetryItem(ViewGroup parent, int messageId, Object... messageFormatArgs) {
        return createUserItem(parent, R.string.attention_ready, messageId, messageFormatArgs);
    }

    protected View createUserItem(ViewGroup parent, int actionId, int messageId,
            Object... messageFormatArgs) {
        View item = mInflater.inflate(R.layout.nls_item, parent, false);
        TextView instructions = item.findViewById(R.id.nls_instructions);
        instructions.setText(getString(messageId, messageFormatArgs));
        Button button = item.findViewById(R.id.nls_action_button);
        button.setText(actionId);
        button.setTag(actionId);
        return item;
    }

    protected ViewGroup createAutoItem(ViewGroup parent, int stringId) {
        ViewGroup item = (ViewGroup) mInflater.inflate(R.layout.nls_item, parent, false);
        TextView instructions = item.findViewById(R.id.nls_instructions);
        instructions.setText(stringId);
        View button = item.findViewById(R.id.nls_action_button);
        button.setVisibility(GONE);
        return item;
    }

    protected View createPassFailItem(ViewGroup parent, @StringRes int textResId) {
        return createPassFailItem(parent, textResId, Resources.ID_NULL);
    }

    protected View createPassFailItem(ViewGroup parent, @StringRes int textResId,
            @DrawableRes int imageResId) {
        View item = mInflater.inflate(R.layout.iva_pass_fail_item, parent, false);
        TextView instructions = item.findViewById(R.id.nls_instructions);
        instructions.setText(textResId);
        ImageView instructionsImage = item.findViewById(R.id.nls_instructions_image);
        instructionsImage.setVisibility(imageResId != Resources.ID_NULL ? VISIBLE : GONE);
        if (imageResId != Resources.ID_NULL) {
            instructionsImage.setImageResource(imageResId);
        }
        return item;
    }

    protected View createUserAndPassFailItem(ViewGroup parent, int actionId, int stringId) {
        View item = mInflater.inflate(R.layout.iva_pass_fail_item, parent, false);
        TextView instructions = item.findViewById(R.id.nls_instructions);
        instructions.setText(stringId);
        Button button = item.findViewById(R.id.nls_action_button);
        button.setVisibility(VISIBLE);
        button.setText(actionId);
        button.setTag(actionId);
        return item;
    }

    // Test management

    abstract protected List<InteractiveTestCase> createTestItems();

    public void run() {
        if (mCurrentTest == null) { return; }
        markItem(mCurrentTest);
        switch (mCurrentTest.status) {
            case SETUP:
                Log.i(TAG, "running setup for: " + mCurrentTest.getClass().getSimpleName());
                mCurrentTest.setUp();
                if (mCurrentTest.status == READY_AFTER_LONG_DELAY) {
                    delay(mCurrentTest.delayTime);
                } else {
                    delay();
                }
                break;

            case WAIT_FOR_USER:
                Log.i(TAG, "waiting for user: " + mCurrentTest.getClass().getSimpleName());
                break;

            case READY_AFTER_LONG_DELAY:
            case RETEST_AFTER_LONG_DELAY:
            case READY:
            case RETEST:
                Log.i(TAG, "running test for: " + mCurrentTest.getClass().getSimpleName());
                try {
                    long startTime = System.currentTimeMillis();
                    mCurrentTest.test();
                    long elapsedTime = System.currentTimeMillis() - startTime;
                    Log.d(TAG, "elapsed test time = " + elapsedTime + " millis");
                    final long kAnrTimeoutSeconds = 5;
                    if (elapsedTime > (kAnrTimeoutSeconds * 1000)) {
                        Log.w(TAG, "WARNING - Sleeping for more than " + kAnrTimeoutSeconds
                                + " seconds in the UI thread might cause an ANR!!");
                    }
                    if (mCurrentTest.status == RETEST_AFTER_LONG_DELAY) {
                        delay(mCurrentTest.delayTime);
                    } else {
                        delay();
                    }
                } catch (Throwable t) {
                    mCurrentTest.status = FAIL;
                    markItem(mCurrentTest);
                    Log.e(TAG, "FAIL: " + mCurrentTest.getClass().getSimpleName(), t);
                    mCurrentTest.tearDown();
                    mCurrentTest = null;
                    delay();
                }

                break;

            case FAIL:
                Log.i(TAG, "FAIL: " + mCurrentTest.getClass().getSimpleName());
                mCurrentTest.tearDown();
                mCurrentTest = null;
                delay();
                break;

            case PASS:
                Log.i(TAG, "pass for: " + mCurrentTest.getClass().getSimpleName());
                mCurrentTest.tearDown();
                if (mTestOrder.hasNext()) {
                    mCurrentTest = mTestOrder.next();
                    Log.i(TAG, "next test is: " + mCurrentTest.getClass().getSimpleName());
                    next();
                } else {
                    Log.i(TAG, "no more tests");
                    mCurrentTest = null;
                    getPassButton().setEnabled(true);
                    mNm.cancelAll();
                }
                break;
        }
        markItem(mCurrentTest);
    }

    /**
     * Return to the state machine to progress through the tests.
     */
    protected void next() {
        mHandler.removeCallbacks(mRunner);
        mHandler.post(mRunner);
    }

    /**
     * Wait for things to settle before returning to the state machine.
     */
    protected void delay() {
        delay(3000);
    }

    protected void sleep(long time) {
        try {
            Thread.sleep(time);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Wait for some time.
     */
    protected void delay(long waitTime) {
        mHandler.removeCallbacks(mRunner);
        mHandler.postDelayed(mRunner, waitTime);
    }

    // UI callbacks

    public void actionPressed(View v) {
        Object tag = v.getTag();
        if (tag instanceof Integer) {
            int id = ((Integer) tag).intValue();
            if (mCurrentTest != null && mCurrentTest.getIntent() != null) {
                startActivity(mCurrentTest.getIntent());
            } else if (id == R.string.attention_ready) {
                if (mCurrentTest != null) {
                    mCurrentTest.status = READY;
                    next();
                }
            }
            if (mCurrentTest != null) {
                mCurrentTest.mUserVerified = true;
                mCurrentTest.buttonPressed = true;
            }
        }
    }

    public void actionPassed(View v) {
        if (mCurrentTest != null) {
            mCurrentTest.mUserVerified = true;
            mCurrentTest.status = PASS;
            next();
        }
    }

    public void actionFailed(View v) {
        if (mCurrentTest != null) {
            mCurrentTest.setFailed();
        }
    }

    // Utilities

    protected PendingIntent makeIntent(int code, String tag) {
        Intent intent = new Intent(tag);
        intent.setComponent(new ComponentName(mContext, DismissService.class));
        PendingIntent pi = PendingIntent.getService(mContext, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE_UNAUDITED);
        return pi;
    }

    protected PendingIntent makeBroadcastIntent(int code, String tag) {
        Intent intent = new Intent(tag);
        intent.setComponent(new ComponentName(mContext, ActionTriggeredReceiver.class));
        PendingIntent pi = PendingIntent.getBroadcast(mContext, code, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE_UNAUDITED);
        return pi;
    }

    protected boolean checkEquals(long[] expected, long[] actual, String message) {
        if (Arrays.equals(expected, actual)) {
            return true;
        }
        logWithStack(String.format(message, Arrays.toString(expected), Arrays.toString(actual)));
        return false;
    }

    protected boolean checkEquals(Object[] expected, Object[] actual, String message) {
        if (Arrays.equals(expected, actual)) {
            return true;
        }
        logWithStack(String.format(message, Arrays.toString(expected), Arrays.toString(actual)));
        return false;
    }

    protected boolean checkEquals(Parcelable expected, Parcelable actual, String message) {
        if (Objects.equals(expected, actual)) {
            return true;
        }
        logWithStack(String.format(message, expected, actual));
        return false;
    }

    protected boolean checkEquals(boolean expected, boolean actual, String message) {
        if (expected == actual) {
            return true;
        }
        logWithStack(String.format(message, expected, actual));
        return false;
    }

    protected boolean checkEquals(long expected, long actual, String message) {
        if (expected == actual) {
            return true;
        }
        logWithStack(String.format(message, expected, actual));
        return false;
    }

    protected boolean checkEquals(CharSequence expected, CharSequence actual, String message) {
        if (expected.equals(actual)) {
            return true;
        }
        logWithStack(String.format(message, expected, actual));
        return false;
    }

    protected boolean checkFlagSet(int expected, int actual, String message) {
        if ((expected & actual) != 0) {
            return true;
        }
        logWithStack(String.format(message, expected, actual));
        return false;
    };

    protected void logWithStack(String message) {
        Throwable stackTrace = new Throwable();
        stackTrace.fillInStackTrace();
        Log.e(TAG, message, stackTrace);
    }

    // Common Tests: useful for the side-effects they generate

    protected class IsEnabledTest extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createNlsSettingsItem(parent, R.string.nls_enable_service);
        }

        @Override
        boolean autoStart() {
            return true;
        }

        @Override
        protected void test() {
            mNm.cancelAll();

            if (getIntent().resolveActivity(mPackageManager) == null) {
                logFail("no settings activity");
                status = FAIL;
            } else {
                String listeners = Secure.getString(getContentResolver(),
                        ENABLED_NOTIFICATION_LISTENERS);
                if (listeners != null && listeners.contains(LISTENER_PATH)) {
                    status = PASS;
                } else {
                    status = WAIT_FOR_USER;
                }
                next();
            }
        }

        @Override
        protected void tearDown() {
            // wait for the service to start
            delay();
        }

        @Override
        protected Intent getIntent() {
            Intent settings = new Intent(ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS);
            settings.putExtra(EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME,
                    MockListener.COMPONENT_NAME.flattenToString());
            return settings;
        }
    }

    protected class ServiceStartedTest extends InteractiveTestCase {
        @Override
        protected View inflate(ViewGroup parent) {
            return createAutoItem(parent, R.string.nls_service_started);
        }

        @Override
        protected void test() {
            if (MockListener.getInstance() != null && MockListener.getInstance().isConnected) {
                status = PASS;
                next();
            } else {
                logFail();
                status = RETEST;
                delay();
            }
        }
    }
}
