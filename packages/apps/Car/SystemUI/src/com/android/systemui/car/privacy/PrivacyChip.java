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

package com.android.systemui.car.privacy;

import android.content.Context;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.AnyThread;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.constraintlayout.motion.widget.MotionLayout;

import com.android.systemui.R;
import com.android.systemui.car.statusicon.AnimatedStatusIcon;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Car optimized Privacy Chip View that is shown when a {@link
 * android.hardware.SensorPrivacyManager.Sensors.Sensor} (such as microphone and camera) is being
 * used.
 *
 * State flows:
 * Base state:
 * <ul>
 * <li>INVISIBLE - Start Sensor Use ->> Sensor Status?</li>
 * </ul>
 * Sensor On:
 * <ul>
 * <li>Sensor Status? - On ->> ACTIVE_INIT</li>
 * <li>ACTIVE_INIT - delay ->> ACTIVE/ACTIVE_SELECTED</li>
 * <li>ACTIVE/ACTIVE_SELECTED - Stop Sensor Use ->> INACTIVE/INACTIVE_SELECTED</li>
 * <li>INACTIVE/INACTIVE_SELECTED - delay/close panel ->> INVISIBLE</li>
 * </ul>
 * Sensor Off:
 * <ul>
 * <li>Sensor Status? - Off ->> SENSOR_OFF</li>
 * <li>SENSOR_OFF - panel opened ->> SENSOR_OFF_SELECTED</li>
 * </ul>
 */
public abstract class PrivacyChip extends MotionLayout implements AnimatedStatusIcon {
    private static final boolean DEBUG = Build.IS_DEBUGGABLE;
    private static final String TAG = "PrivacyChip";

    private final int mDelayPillToCircle;
    private final int mDelayToNoSensorUsage;

    private AnimationStates mCurrentTransitionState;
    private boolean mPanelOpen;
    private boolean mIsInflated;
    private boolean mIsSensorEnabled;
    private ScheduledExecutorService mExecutor;

    public PrivacyChip(@NonNull Context context) {
        this(context, /* attrs= */ null);
    }

    public PrivacyChip(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, /* defStyleAttrs= */ 0);
    }

    public PrivacyChip(@NonNull Context context,
            @Nullable AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);

        mDelayPillToCircle = getResources().getInteger(R.integer.privacy_chip_pill_to_circle_delay);
        mDelayToNoSensorUsage =
                getResources().getInteger(R.integer.privacy_chip_no_sensor_usage_delay);

        mExecutor = Executors.newSingleThreadScheduledExecutor();
        mIsInflated = false;

        // The sensor is enabled by default (invisible state).
        mIsSensorEnabled = true;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        mCurrentTransitionState = AnimationStates.INVISIBLE;
        mIsInflated = true;

        ImageView lightMutedIcon = requireViewById(R.id.light_muted_icon);
        lightMutedIcon.setImageResource(getLightMutedIconResourceId());
        ImageView darkMutedIcon = requireViewById(R.id.dark_muted_icon);
        darkMutedIcon.setImageResource(getDarkMutedIconResourceId());
        ImageView lightIcon = requireViewById(R.id.light_icon);
        lightIcon.setImageResource(getLightIconResourceId());
        ImageView darkIcon = requireViewById(R.id.dark_icon);
        darkIcon.setImageResource(getDarkIconResourceId());

        setTransitionListener(
                new MotionLayout.TransitionListener() {
                    @Override
                    public void onTransitionCompleted(MotionLayout motionLayout, int currentId) {}

                    @Override
                    public void onTransitionStarted(MotionLayout m, int startId, int endId) {
                        if (startId == R.id.active) {
                            showIndicatorBorder(false);
                        }
                    }

                    @Override
                    public void onTransitionChange(
                            MotionLayout m, int startId, int endId, float progress) {
                        // When R.id.activeFromActiveInit animation is done and the green
                        // indicator shows up, set its background with a drawable with border.
                        // Reset the background to default after that (in onTransitionStarted()).
                        if (Float.compare(progress, 1.0f) == 0
                                && startId == R.id.active_init && endId == R.id.active) {
                            showIndicatorBorder(true);
                        }
                    }

                    @Override
                    public void onTransitionTrigger(
                            MotionLayout m, int triggerId, boolean positive, float p) {}
                });
    }

    @Override
    public void setOnClickListener(View.OnClickListener onClickListener) {
        // required for CTS tests.
        super.setOnClickListener(onClickListener);
        // required for rotary.
        requireViewById(R.id.focus_view).setOnClickListener(onClickListener);
    }

    /**
     * Sets whether the sensor is enabled or disabled.
     * If enabled, animates to {@link AnimationStates#INVISIBLE}.
     * Otherwise, animates to {@link AnimationStates#SENSOR_OFF}.
     */
    @UiThread
    public void setSensorEnabled(boolean enabled) {
        if (DEBUG) Log.d(TAG, getSensorNameWithFirstLetterCapitalized() + " enabled: " + enabled);

        if (mIsSensorEnabled == enabled) {
            if (enabled) {
                switch (mCurrentTransitionState) {
                    case INVISIBLE:
                    case ACTIVE:
                    case ACTIVE_SELECTED:
                    case INACTIVE:
                    case INACTIVE_SELECTED:
                    case ACTIVE_INIT:
                        return;
                }
            } else {
                if (mCurrentTransitionState == AnimationStates.SENSOR_OFF
                        || mCurrentTransitionState == AnimationStates.SENSOR_OFF_SELECTED) {
                    return;
                }
            }
        }

        mIsSensorEnabled = enabled;

        if (!mIsInflated) {
            if (DEBUG) Log.d(TAG, "Layout not inflated");

            return;
        }

        if (mIsSensorEnabled) {
            if (mPanelOpen) {
                setTransition(R.id.inactiveSelectedFromSensorOffSelected);
            } else {
                setTransition(R.id.invisibleFromSensorOff);
            }
        } else {
            if (mPanelOpen) {
                switch (mCurrentTransitionState) {
                    case INVISIBLE:
                        setTransition(R.id.sensorOffSelectedFromInvisible);
                        break;
                    case ACTIVE_INIT:
                        setTransition(R.id.sensorOffSelectedFromActiveInit);
                        break;
                    case ACTIVE:
                        setTransition(R.id.sensorOffSelectedFromActive);
                        break;
                    case ACTIVE_SELECTED:
                        setTransition(R.id.sensorOffSelectedFromActiveSelected);
                        break;
                    case INACTIVE:
                        setTransition(R.id.sensorOffSelectedFromInactive);
                        break;
                    case INACTIVE_SELECTED:
                        setTransition(R.id.sensorOffSelectedFromInactiveSelected);
                        break;
                    default:
                        return;
                }
            } else {
                switch (mCurrentTransitionState) {
                    case INVISIBLE:
                        setTransition(R.id.sensorOffFromInvisible);
                        break;
                    case ACTIVE_INIT:
                        setTransition(R.id.sensorOffFromActiveInit);
                        break;
                    case ACTIVE:
                        setTransition(R.id.sensorOffFromActive);
                        break;
                    case ACTIVE_SELECTED:
                        setTransition(R.id.sensorOffFromActiveSelected);
                        break;
                    case INACTIVE:
                        setTransition(R.id.sensorOffFromInactive);
                        break;
                    case INACTIVE_SELECTED:
                        setTransition(R.id.sensorOffFromInactiveSelected);
                        break;
                    default:
                        return;
                }
            }
        }

        mExecutor.shutdownNow();
        mExecutor = Executors.newSingleThreadScheduledExecutor();

        // TODO(182938429): Use Transition Listeners once ConstraintLayout 2.0.0 is being used.

        // When the sensor is off, privacy chip is always visible.
        if (!mIsSensorEnabled) setVisibility(View.VISIBLE);
        setContentDescription(!mIsSensorEnabled);
        if (mIsSensorEnabled) {
            if (mPanelOpen) {
                mCurrentTransitionState = AnimationStates.INACTIVE_SELECTED;
            } else {
                mCurrentTransitionState = AnimationStates.INVISIBLE;
            }
        } else {
            if (mPanelOpen) {
                mCurrentTransitionState = AnimationStates.SENSOR_OFF_SELECTED;
            } else {
                mCurrentTransitionState = AnimationStates.SENSOR_OFF;
            }
        }
        transitionToEnd();
        if (mIsSensorEnabled && !mPanelOpen) setVisibility(View.GONE);
    }

    protected void setContentDescription(boolean isSensorOff) {
        String contentDescription;
        if (isSensorOff) {
            contentDescription = getResources().getString(R.string.privacy_chip_off_content,
                    getSensorNameWithFirstLetterCapitalized());
        } else {
            contentDescription = getResources().getString(
                    R.string.ongoing_privacy_chip_content_multiple_apps, getSensorName());
        }
        setContentDescription(contentDescription);
    }

    /**
     * Starts reveal animation for Privacy Chip.
     */
    @UiThread
    public void animateIn() {
        if (!mIsInflated) {
            if (DEBUG) Log.d(TAG, "Layout not inflated");

            return;
        }

        if (mCurrentTransitionState == null) {
            if (DEBUG) Log.d(TAG, "Current transition state is null or empty.");

            return;
        }

        switch (mCurrentTransitionState) {
            case INVISIBLE:
                setTransition(mIsSensorEnabled ? R.id.activeInitFromInvisible
                        : R.id.sensorOffFromInvisible);
                break;
            case INACTIVE:
                setTransition(mIsSensorEnabled ? R.id.activeInitFromInactive
                        : R.id.sensorOffFromInactive);
                break;
            case INACTIVE_SELECTED:
                setTransition(mIsSensorEnabled ? R.id.activeInitFromInactiveSelected
                        : R.id.sensorOffFromInactiveSelected);
                break;
            case SENSOR_OFF:
                if (!mIsSensorEnabled) {
                    if (DEBUG) {
                        Log.d(TAG, "No Transition.");
                    }
                    return;
                }

                setTransition(R.id.activeInitFromSensorOff);
                break;
            case SENSOR_OFF_SELECTED:
                if (!mIsSensorEnabled) {
                    if (DEBUG) {
                        Log.d(TAG, "No Transition.");
                    }
                    return;
                }

                setTransition(R.id.activeInitFromSensorOffSelected);
                break;
            default:
                if (DEBUG) {
                    Log.d(TAG, "Early exit, mCurrentTransitionState= "
                            + mCurrentTransitionState);
                }

                return;
        }

        mExecutor.shutdownNow();
        mExecutor = Executors.newSingleThreadScheduledExecutor();

        // TODO(182938429): Use Transition Listeners once ConstraintLayout 2.0.0 is being used.
        setContentDescription(false);
        setVisibility(View.VISIBLE);
        if (mIsSensorEnabled) {
            mCurrentTransitionState = AnimationStates.ACTIVE_INIT;
        } else {
            if (mPanelOpen) {
                mCurrentTransitionState = AnimationStates.SENSOR_OFF_SELECTED;
            } else {
                mCurrentTransitionState = AnimationStates.SENSOR_OFF;
            }
        }
        transitionToEnd();
        if (mIsSensorEnabled) {
            mExecutor.schedule(PrivacyChip.this::animateToOrangeCircle, mDelayPillToCircle,
                    TimeUnit.MILLISECONDS);
        }
    }

    // TODO(182938429): Use Transition Listeners once ConstraintLayout 2.0.0 is being used.
    private void animateToOrangeCircle() {
        // Since this is launched using a {@link ScheduledExecutorService}, its UI based elements
        // need to execute on main executor.
        getContext().getMainExecutor().execute(() -> {
            if (mPanelOpen) {
                setTransition(R.id.activeSelectedFromActiveInit);
                mCurrentTransitionState = AnimationStates.ACTIVE_SELECTED;
            } else {
                setTransition(R.id.activeFromActiveInit);
                mCurrentTransitionState = AnimationStates.ACTIVE;
            }
            transitionToEnd();
        });
    }

    private void showIndicatorBorder(boolean show) {
        // Since this is launched using a {@link ScheduledExecutorService}, its UI based elements
        // need to execute on main executor.
        getContext().getMainExecutor().execute(() -> {
            View activeBackground = findViewById(R.id.active_background);
            activeBackground.setBackground(getContext().getDrawable(show
                    ? R.drawable.privacy_chip_active_background_pill_with_border
                    : R.drawable.privacy_chip_active_background_pill));
        });
    }

    /**
     * Starts conceal animation for Privacy Chip.
     */
    @UiThread
    public void animateOut() {
        if (!mIsInflated) {
            if (DEBUG) Log.d(TAG, "Layout not inflated");

            return;
        }

        if (mPanelOpen) {
            switch (mCurrentTransitionState) {
                case ACTIVE_INIT:
                    setTransition(R.id.inactiveSelectedFromActiveInit);
                    break;
                case ACTIVE:
                    setTransition(R.id.inactiveSelectedFromActive);
                    break;
                case ACTIVE_SELECTED:
                    setTransition(R.id.inactiveSelectedFromActiveSelected);
                    break;
                default:
                    if (DEBUG) {
                        Log.d(TAG, "Early exit, mCurrentTransitionState= "
                                + mCurrentTransitionState);
                    }

                    return;
            }
        } else {
            switch (mCurrentTransitionState) {
                case ACTIVE_INIT:
                    setTransition(R.id.inactiveFromActiveInit);
                    break;
                case ACTIVE:
                    setTransition(R.id.inactiveFromActive);
                    break;
                case ACTIVE_SELECTED:
                    setTransition(R.id.inactiveFromActiveSelected);
                    break;
                default:
                    if (DEBUG) {
                        Log.d(TAG, "Early exit, mCurrentTransitionState= "
                                + mCurrentTransitionState);
                    }

                    return;
            }
        }

        mExecutor.shutdownNow();
        mExecutor = Executors.newSingleThreadScheduledExecutor();

        // TODO(182938429): Use Transition Listeners once ConstraintLayout 2.0.0 is being used.
        mCurrentTransitionState = mPanelOpen
                ? AnimationStates.INACTIVE_SELECTED
                : AnimationStates.INACTIVE;
        transitionToEnd();
        mExecutor.schedule(PrivacyChip.this::reset, mDelayToNoSensorUsage,
                TimeUnit.MILLISECONDS);
    }



    // TODO(182938429): Use Transition Listeners once ConstraintLayout 2.0.0 is being used.
    private void reset() {
        // Since this is launched using a {@link ScheduledExecutorService}, its UI based elements
        // need to execute on main executor.
        getContext().getMainExecutor().execute(() -> {
            if (mIsSensorEnabled && !mPanelOpen) {
                setTransition(R.id.invisibleFromInactive);
                mCurrentTransitionState = AnimationStates.INVISIBLE;
            } else if (!mIsSensorEnabled) {
                if (mPanelOpen) {
                    setTransition(R.id.inactiveSelectedFromSensorOffSelected);
                    mCurrentTransitionState = AnimationStates.INACTIVE_SELECTED;
                } else {
                    setTransition(R.id.invisibleFromSensorOff);
                    mCurrentTransitionState = AnimationStates.INVISIBLE;
                }
            }

            transitionToEnd();

            if (!mPanelOpen) {
                setVisibility(View.GONE);
            }
        });
    }

    @AnyThread
    @Override
    public void setIconHighlighted(boolean iconHighlighted) {
        // UI based elements need to execute on main executor.
        getContext().getMainExecutor().execute(() -> {
            if (mPanelOpen == iconHighlighted) {
                return;
            }

            mPanelOpen = iconHighlighted;

            if (mIsSensorEnabled) {
                switch (mCurrentTransitionState) {
                    case ACTIVE:
                        if (mPanelOpen) {
                            setTransition(R.id.activeSelectedFromActive);
                            mCurrentTransitionState = AnimationStates.ACTIVE_SELECTED;
                            transitionToEnd();
                        }
                        return;
                    case ACTIVE_SELECTED:
                        if (!mPanelOpen) {
                            setTransition(R.id.activeFromActiveSelected);
                            mCurrentTransitionState = AnimationStates.ACTIVE;
                            transitionToEnd();
                        }
                        return;
                    case INACTIVE:
                        if (mPanelOpen) {
                            setTransition(R.id.inactiveSelectedFromInactive);
                            mCurrentTransitionState = AnimationStates.INACTIVE_SELECTED;
                            transitionToEnd();
                        }
                        return;
                    case INACTIVE_SELECTED:
                        if (!mPanelOpen) {
                            setTransition(R.id.invisibleFromInactiveSelected);
                            mCurrentTransitionState = AnimationStates.INVISIBLE;
                            transitionToEnd();
                            setVisibility(View.GONE);
                        }
                        return;
                }
            } else {
                switch (mCurrentTransitionState) {
                    case SENSOR_OFF:
                        if (mPanelOpen) {
                            setTransition(R.id.sensorOffSelectedFromSensorOff);
                            mCurrentTransitionState = AnimationStates.SENSOR_OFF_SELECTED;
                            transitionToEnd();
                        }
                        return;
                    case SENSOR_OFF_SELECTED:
                        if (!mPanelOpen) {
                            setTransition(R.id.sensorOffFromSensorOffSelected);
                            mCurrentTransitionState = AnimationStates.SENSOR_OFF;
                            transitionToEnd();
                        }
                        return;
                }
            }

            if (DEBUG) {
                Log.d(TAG, "Early exit, mCurrentTransitionState= "
                        + mCurrentTransitionState);
            }
        });
    }

    @Override
    public void setTransition(int transitionId) {
        if (DEBUG) {
            Log.d(TAG, "Transition set: " + getResources().getResourceEntryName(transitionId));
        }

        // Sometimes the alpha of the icon is reset to 0 incorrectly after several transitions, so
        // set it to 1 before each transition as a workaround. This is fine as long as the
        // visibility of the icon is set properly. See b/226651461.
        View darkIcon = requireViewById(R.id.dark_icon);
        darkIcon.setAlpha(1.0f);

        super.setTransition(transitionId);
    }

    protected abstract @DrawableRes int getLightMutedIconResourceId();

    protected abstract @DrawableRes int getDarkMutedIconResourceId();

    protected abstract @DrawableRes int getLightIconResourceId();

    protected abstract @DrawableRes int getDarkIconResourceId();

    protected abstract String getSensorName();

    protected abstract String getSensorNameWithFirstLetterCapitalized();

    private enum AnimationStates {
        INVISIBLE,
        ACTIVE_INIT,
        ACTIVE,
        ACTIVE_SELECTED,
        INACTIVE,
        INACTIVE_SELECTED,
        SENSOR_OFF,
        SENSOR_OFF_SELECTED,
    }
}
