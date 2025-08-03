/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm.app;

import static android.server.wm.app.Components.PipActivity.ACTION_CHANGE_ASPECT_RATIO;
import static android.server.wm.app.Components.PipActivity.ACTION_ENTER_PIP;
import static android.server.wm.app.Components.PipActivity.ACTION_ENTER_PIP_AND_WAIT_FOR_UI_STATE;
import static android.server.wm.app.Components.PipActivity.ACTION_EXPAND_PIP;
import static android.server.wm.app.Components.PipActivity.ACTION_FINISH;
import static android.server.wm.app.Components.PipActivity.ACTION_LAUNCH_TRANSLUCENT_ACTIVITY;
import static android.server.wm.app.Components.PipActivity.ACTION_MOVE_TO_BACK;
import static android.server.wm.app.Components.PipActivity.ACTION_ON_PIP_REQUESTED;
import static android.server.wm.app.Components.PipActivity.ACTION_SET_ON_PAUSE_REMOTE_CALLBACK;
import static android.server.wm.app.Components.PipActivity.ACTION_SET_REQUESTED_ORIENTATION;
import static android.server.wm.app.Components.PipActivity.ACTION_UPDATE_PIP_STATE;
import static android.server.wm.app.Components.PipActivity.EXTRA_ALLOW_AUTO_PIP;
import static android.server.wm.app.Components.PipActivity.EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP;
import static android.server.wm.app.Components.PipActivity.EXTRA_CLOSE_ACTION;
import static android.server.wm.app.Components.PipActivity.EXTRA_DISMISS_KEYGUARD;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP_ON_BACK_PRESSED;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP_ON_PAUSE;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP_ON_PIP_REQUESTED;
import static android.server.wm.app.Components.PipActivity.EXTRA_ENTER_PIP_ON_USER_LEAVE_HINT;
import static android.server.wm.app.Components.PipActivity.EXTRA_EXPANDED_PIP_ASPECT_RATIO_DENOMINATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_EXPANDED_PIP_ASPECT_RATIO_NUMERATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_FINISH_SELF_ON_RESUME;
import static android.server.wm.app.Components.PipActivity.EXTRA_IS_SEAMLESS_RESIZE_ENABLED;
import static android.server.wm.app.Components.PipActivity.EXTRA_NUMBER_OF_CUSTOM_ACTIONS;
import static android.server.wm.app.Components.PipActivity.EXTRA_ON_PAUSE_DELAY;
import static android.server.wm.app.Components.PipActivity.EXTRA_PIP_ON_PAUSE_CALLBACK;
import static android.server.wm.app.Components.PipActivity.EXTRA_PIP_ORIENTATION;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_ASPECT_RATIO_DENOMINATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_ASPECT_RATIO_NUMERATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_PIP_CALLBACK;
import static android.server.wm.app.Components.PipActivity.EXTRA_SET_PIP_STASHED;
import static android.server.wm.app.Components.PipActivity.EXTRA_SHOW_OVER_KEYGUARD;
import static android.server.wm.app.Components.PipActivity.EXTRA_START_ACTIVITY;
import static android.server.wm.app.Components.PipActivity.EXTRA_SUBTITLE;
import static android.server.wm.app.Components.PipActivity.EXTRA_TAP_TO_FINISH;
import static android.server.wm.app.Components.PipActivity.EXTRA_TITLE;
import static android.server.wm.app.Components.PipActivity.IS_IN_PIP_MODE_RESULT;
import static android.server.wm.app.Components.PipActivity.UI_STATE_ENTERING_PIP_RESULT;
import static android.server.wm.app.Components.PipActivity.UI_STATE_STASHED_RESULT;
import static android.view.WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.PictureInPictureParams;
import android.app.PictureInPictureUiState;
import android.app.RemoteAction;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteCallback;
import android.os.SystemClock;
import android.server.wm.CommandSession;
import android.util.Log;
import android.util.Rational;

import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PipActivity extends AbstractLifecycleLogActivity {

    private boolean mEnteredPictureInPicture;
    private boolean mEnterPipOnBackPressed;
    private RemoteCallback mCb;
    private RemoteCallback mOnPauseCallback;

    private Handler mHandler = new Handler();
    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null) {
                switch (intent.getAction()) {
                    case ACTION_ENTER_PIP:
                        enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
                        if (intent.getExtras() != null) {
                            mCb = (RemoteCallback) intent.getExtras().get(EXTRA_SET_PIP_CALLBACK);
                            if (mCb != null) {
                                mCb.sendResult(new Bundle());
                            }
                        }
                        break;
                    case ACTION_ENTER_PIP_AND_WAIT_FOR_UI_STATE:
                        // mCb will be callback-ed in onPictureInPictureUiStateChanged.
                        mCb = (RemoteCallback) intent.getExtras().get(EXTRA_SET_PIP_CALLBACK);
                        enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
                        break;
                    case ACTION_MOVE_TO_BACK:
                        moveTaskToBack(false /* nonRoot */);
                        break;
                    case ACTION_UPDATE_PIP_STATE:
                        mCb = (RemoteCallback) intent.getExtras().get(EXTRA_SET_PIP_CALLBACK);
                        boolean stashed = intent.getBooleanExtra(EXTRA_SET_PIP_STASHED, false);
                        onPictureInPictureUiStateChanged(new PictureInPictureUiState(stashed));
                        break;
                    case ACTION_EXPAND_PIP:
                        // Trigger the activity to expand
                        Intent startIntent = new Intent(PipActivity.this, PipActivity.class);
                        startIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(startIntent);

                        if (intent.hasExtra(EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR)
                                && intent.hasExtra(EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR)) {
                            // Ugly, but required to wait for the startActivity to actually start
                            // the activity...
                            mHandler.postDelayed(() -> {
                                final PictureInPictureParams.Builder builder =
                                        new PictureInPictureParams.Builder();
                                builder.setAspectRatio(getAspectRatio(intent,
                                        EXTRA_SET_ASPECT_RATIO_WITH_DELAY_NUMERATOR,
                                        EXTRA_SET_ASPECT_RATIO_WITH_DELAY_DENOMINATOR));
                                setPictureInPictureParams(builder.build());
                            }, 100);
                        }
                        break;
                    case ACTION_SET_REQUESTED_ORIENTATION:
                        setRequestedOrientation(Integer.parseInt(intent.getStringExtra(
                                EXTRA_PIP_ORIENTATION)));
                        break;
                    case ACTION_FINISH:
                        finish();
                        break;
                    case ACTION_ON_PIP_REQUESTED:
                        onPictureInPictureRequested();
                        break;
                    case ACTION_CHANGE_ASPECT_RATIO:
                        setPictureInPictureParams(new PictureInPictureParams.Builder()
                                .setAspectRatio(getAspectRatio(intent,
                                        EXTRA_SET_ASPECT_RATIO_NUMERATOR,
                                        EXTRA_SET_ASPECT_RATIO_DENOMINATOR))
                                .build());
                        break;
                    case ACTION_LAUNCH_TRANSLUCENT_ACTIVITY:
                        startActivity(new Intent(PipActivity.this, TranslucentTestActivity.class));
                        break;
                    case ACTION_SET_ON_PAUSE_REMOTE_CALLBACK:
                        mOnPauseCallback = intent.getParcelableExtra(
                                EXTRA_PIP_ON_PAUSE_CALLBACK, RemoteCallback.class);
                        // Signals the caller that we have received the mOnPauseCallback
                        final RemoteCallback setCallback = intent.getParcelableExtra(
                                EXTRA_SET_PIP_CALLBACK, RemoteCallback.class);
                        setCallback.sendResult(Bundle.EMPTY);
                        break;
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the fixed orientation if requested
        if (getIntent().hasExtra(EXTRA_PIP_ORIENTATION)) {
            final int ori = Integer.parseInt(getIntent().getStringExtra(EXTRA_PIP_ORIENTATION));
            setRequestedOrientation(ori);
        }

        // Set the window flag to show over the keyguard
        setShowWhenLocked(parseBooleanExtra(EXTRA_SHOW_OVER_KEYGUARD));

        // Set the window flag to dismiss the keyguard
        if (parseBooleanExtra(EXTRA_DISMISS_KEYGUARD)) {
            getWindow().addFlags(FLAG_DISMISS_KEYGUARD);
        }

        boolean enteringPip = false;
        // Enter picture in picture with the given aspect ratio if provided
        if (parseBooleanExtra(EXTRA_ENTER_PIP)) {
            if (getIntent().hasExtra(EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR)
                    && getIntent().hasExtra(EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR)) {
                try {
                    final PictureInPictureParams.Builder builder =
                            new PictureInPictureParams.Builder();
                    builder.setAspectRatio(getAspectRatio(getIntent(),
                            EXTRA_ENTER_PIP_ASPECT_RATIO_NUMERATOR,
                            EXTRA_ENTER_PIP_ASPECT_RATIO_DENOMINATOR));
                    if (shouldAddExpandedPipAspectRatios()) {
                        builder.setExpandedAspectRatio(getAspectRatio(getIntent(),
                                EXTRA_EXPANDED_PIP_ASPECT_RATIO_NUMERATOR,
                                EXTRA_EXPANDED_PIP_ASPECT_RATIO_DENOMINATOR));
                    }
                    enteringPip = enterPictureInPictureMode(builder.build());
                } catch (Exception e) {
                    // This call can fail intentionally if the aspect ratio is too extreme
                }
            } else {
                enteringPip = enterPictureInPictureMode(
                        new PictureInPictureParams.Builder().build());
            }
        }

        // We need to wait for either enterPictureInPicture() or requestAutoEnterPictureInPicture()
        // to be called before setting the aspect ratio
        if (getIntent().hasExtra(EXTRA_SET_ASPECT_RATIO_NUMERATOR)
                && getIntent().hasExtra(EXTRA_SET_ASPECT_RATIO_DENOMINATOR)) {
            final PictureInPictureParams.Builder builder =
                    new PictureInPictureParams.Builder();
            builder.setAspectRatio(getAspectRatio(getIntent(),
                    EXTRA_SET_ASPECT_RATIO_NUMERATOR, EXTRA_SET_ASPECT_RATIO_DENOMINATOR));
            try {
                setPictureInPictureParams(builder.build());
            } catch (Exception e) {
                // This call can fail intentionally if the aspect ratio is too extreme
            }
        }

        final PictureInPictureParams.Builder sharedBuilder = new PictureInPictureParams.Builder();
        boolean sharedBuilderChanged = false;

        if (parseBooleanExtra(EXTRA_ALLOW_AUTO_PIP)) {
            sharedBuilder.setAutoEnterEnabled(true);
            sharedBuilderChanged = true;
        }

        if (getIntent().hasExtra(EXTRA_IS_SEAMLESS_RESIZE_ENABLED)) {
            sharedBuilder.setSeamlessResizeEnabled(
                    getIntent().getBooleanExtra(EXTRA_IS_SEAMLESS_RESIZE_ENABLED, true));
            sharedBuilderChanged = true;
        }

        if (getIntent().hasExtra(EXTRA_TITLE)) {
            sharedBuilder.setTitle(getIntent().getStringExtra(EXTRA_TITLE));
            sharedBuilderChanged = true;
        }

        if (getIntent().hasExtra(EXTRA_SUBTITLE)) {
            sharedBuilder.setSubtitle(getIntent().getStringExtra(EXTRA_SUBTITLE));
            sharedBuilderChanged = true;
        }

        if (getIntent().hasExtra(EXTRA_CLOSE_ACTION)) {
            if (getIntent().getBooleanExtra(EXTRA_CLOSE_ACTION, false)) {
                sharedBuilder.setCloseAction(createRemoteAction(0));
            } else {
                sharedBuilder.setCloseAction(null);
            }
            sharedBuilderChanged = true;
        }

        // Enable tap to finish if necessary
        if (parseBooleanExtra(EXTRA_TAP_TO_FINISH)) {
            setContentView(R.layout.tap_to_finish_pip_layout);
            findViewById(R.id.content).setOnClickListener(v -> {
                finish();
            });
        }

        // Launch a new activity if requested
        String launchActivityComponent = getIntent().getStringExtra(EXTRA_START_ACTIVITY);
        if (launchActivityComponent != null) {
            Intent launchIntent = new Intent();
            launchIntent.setComponent(ComponentName.unflattenFromString(launchActivityComponent));
            startActivity(launchIntent);
        }

        // Set custom actions if requested
        if (getIntent().hasExtra(EXTRA_NUMBER_OF_CUSTOM_ACTIONS)) {
            final int numberOfCustomActions = Integer.valueOf(
                    getIntent().getStringExtra(EXTRA_NUMBER_OF_CUSTOM_ACTIONS));
            final List<RemoteAction> actions = new ArrayList<>(numberOfCustomActions);
            for (int i = 0; i< numberOfCustomActions; i++) {
                actions.add(createRemoteAction(i));
            }
            sharedBuilder.setActions(actions);
            sharedBuilderChanged = true;
        }

        if (sharedBuilderChanged) {
            setPictureInPictureParams(sharedBuilder.build());
        }

        // Register the broadcast receiver
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_ENTER_PIP);
        filter.addAction(ACTION_MOVE_TO_BACK);
        filter.addAction(ACTION_EXPAND_PIP);
        filter.addAction(ACTION_UPDATE_PIP_STATE);
        filter.addAction(ACTION_ENTER_PIP_AND_WAIT_FOR_UI_STATE);
        filter.addAction(ACTION_SET_REQUESTED_ORIENTATION);
        filter.addAction(ACTION_FINISH);
        filter.addAction(ACTION_ON_PIP_REQUESTED);
        filter.addAction(ACTION_CHANGE_ASPECT_RATIO);
        filter.addAction(ACTION_LAUNCH_TRANSLUCENT_ACTIVITY);
        filter.addAction(ACTION_SET_ON_PAUSE_REMOTE_CALLBACK);
        registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED);

        // Don't dump configuration when entering PIP to avoid the verifier getting the intermediate
        // state. In this case it is expected that the verifier will check the changed configuration
        // after onConfigurationChanged.
        if (!enteringPip) {
            // Dump applied display metrics.
            dumpConfiguration(getResources().getConfiguration());
            dumpConfigInfo();
        }

        mEnterPipOnBackPressed = parseBooleanExtra(EXTRA_ENTER_PIP_ON_BACK_PRESSED);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Finish self if requested
        if (parseBooleanExtra(EXTRA_FINISH_SELF_ON_RESUME)) {
            finish();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Pause if requested
        if (getIntent().hasExtra(EXTRA_ON_PAUSE_DELAY)) {
            SystemClock.sleep(Long.valueOf(getIntent().getStringExtra(EXTRA_ON_PAUSE_DELAY)));
        }

        // Enter PIP on move to background
        if (parseBooleanExtra(EXTRA_ENTER_PIP_ON_PAUSE)) {
            enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
        }

        if (mOnPauseCallback != null) {
            Bundle res = new Bundle(1);
            res.putBoolean(IS_IN_PIP_MODE_RESULT, isInPictureInPictureMode());
            mOnPauseCallback.sendResult(res);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();

        if (parseBooleanExtra(EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP) && !mEnteredPictureInPicture) {
            Log.w(getTag(), "Unexpected onStop() called before entering picture-in-picture");
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        unregisterReceiver(mReceiver);
    }

    @Override
    protected void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (parseBooleanExtra(EXTRA_ENTER_PIP_ON_USER_LEAVE_HINT)) {
            enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
        }
    }

    @Override
    public boolean onPictureInPictureRequested() {
        onCallback(CommandSession.ActivityCallback.ON_PICTURE_IN_PICTURE_REQUESTED);
        if (parseBooleanExtra(EXTRA_ENTER_PIP_ON_PIP_REQUESTED)) {
            enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
            return true;
        }
        return super.onPictureInPictureRequested();
    }

    @Override
    public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode,
            Configuration newConfig) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);

        // Fail early if the activity state does not match the dispatched state
        if (isInPictureInPictureMode() != isInPictureInPictureMode) {
            Log.w(getTag(), "Received onPictureInPictureModeChanged mode="
                    + isInPictureInPictureMode + " activityState=" + isInPictureInPictureMode());
            finish();
        }

        // Mark that we've entered picture-in-picture so that we can stop checking for
        // EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP
        if (isInPictureInPictureMode) {
            mEnteredPictureInPicture = true;
        }
    }

    @Override
    public void onPictureInPictureUiStateChanged(PictureInPictureUiState pipState) {
        Bundle res = new Bundle();
        res.putBoolean(UI_STATE_STASHED_RESULT, pipState.isStashed());
        res.putBoolean(UI_STATE_ENTERING_PIP_RESULT, pipState.isTransitioningToPip());
        if (mCb != null) {
            mCb.sendResult(res);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        dumpConfiguration(newConfig);
        dumpConfigInfo();
    }

    @Override
    public void onBackPressed() {
        if (mEnterPipOnBackPressed) {
            enterPictureInPictureMode(new PictureInPictureParams.Builder().build());
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Launches a new instance of the PipActivity in the same task that will automatically enter
     * PiP.
     */
    static void launchEnterPipActivity(Activity caller, @Nullable Bundle overrides) {
        final Intent intent = new Intent(caller, PipActivity.class);
        intent.putExtra(EXTRA_ENTER_PIP, "true");
        intent.putExtra(EXTRA_ASSERT_NO_ON_STOP_BEFORE_PIP, "true");
        if (overrides != null) {
            intent.putExtras(overrides);
        }
        caller.startActivity(intent);
    }

    private boolean parseBooleanExtra(String key) {
        return getIntent().hasExtra(key) && Boolean.parseBoolean(getIntent().getStringExtra(key));
    }

    /**
     * @return a {@link Rational} aspect ratio from the given intent and extras.
     */
    private Rational getAspectRatio(Intent intent, String extraNum, String extraDenom) {
        return new Rational(
                Integer.valueOf(intent.getStringExtra(extraNum)),
                Integer.valueOf(intent.getStringExtra(extraDenom)));
    }

    /** @return {@link RemoteAction} instance titled after a given index */
    private RemoteAction createRemoteAction(int index) {
        return new RemoteAction(Icon.createWithResource(this, R.drawable.red),
                "action " + index,
                "contentDescription " + index,
                PendingIntent.getBroadcast(this, 0, new Intent(), PendingIntent.FLAG_IMMUTABLE));
    }

    private boolean shouldAddExpandedPipAspectRatios() {
        return getIntent().hasExtra(EXTRA_EXPANDED_PIP_ASPECT_RATIO_NUMERATOR)
            && getIntent().hasExtra(EXTRA_EXPANDED_PIP_ASPECT_RATIO_DENOMINATOR);
    }
}
