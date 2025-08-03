/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.tv.twopanelsettings.slices;

import static androidx.lifecycle.Lifecycle.Event.ON_CREATE;
import static androidx.lifecycle.Lifecycle.Event.ON_DESTROY;
import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;
import static androidx.lifecycle.Lifecycle.Event.ON_START;
import static androidx.lifecycle.Lifecycle.Event.ON_STOP;

import static com.android.tv.twopanelsettings.slices.InstrumentationUtils.logPageFocused;

import android.animation.AnimatorInflater;
import android.app.tvsettings.TvSettingsEnums;
import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.lifecycle.LifecycleOwner;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.tv.twopanelsettings.R;
import com.android.tv.twopanelsettings.SettingsPreferenceFragmentBase;
import com.android.tv.twopanelsettings.TwoPanelSettingsFragment;

/**
 * A copy of SettingsPreferenceFragment in Settings.
 */
public abstract class SettingsPreferenceFragment extends SettingsPreferenceFragmentBase
        implements LifecycleOwner,
        TwoPanelSettingsFragment.PreviewableComponentCallback {
    private final Lifecycle mLifecycle = new Lifecycle(this);

    // Rename getLifecycle() to getSettingsLifecycle() as androidx Fragment has already implemented
    // getLifecycle(), overriding here would cause unexpected crash in framework.
    @NonNull
    public Lifecycle getSettingsLifecycle() {
        return mLifecycle;
    }

    public SettingsPreferenceFragment() {
    }

    @CallSuper
    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mLifecycle.onAttach(context);
    }

    @CallSuper
    @Override
    public void onCreate(Bundle savedInstanceState) {
        mLifecycle.onCreate(savedInstanceState);
        mLifecycle.handleLifecycleEvent(ON_CREATE);
        super.onCreate(savedInstanceState);
        if (getCallbackFragment() != null
                && !(getCallbackFragment() instanceof TwoPanelSettingsFragment)) {
            logPageFocused(getPageId(), true);
        }
    }

    // We explicitly set the title gravity to RIGHT in RTL cases to remedy some complicated gravity
    // issues. For more details, please read the comment of onViewCreated() in
    // com.android.tv.settings.SettingsPreferenceFragment.
    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (view != null) {
            TextView titleView = view.findViewById(R.id.decor_title);
            // We rely on getResources().getConfiguration().getLayoutDirection() instead of
            // view.isLayoutRtl() as the latter could return false in some complex scenarios even if
            // it is RTL.
            if (titleView != null
                    && getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_RTL) {
                titleView.setGravity(Gravity.RIGHT);
            }
        }
    }

    @Override
    protected RecyclerView.Adapter onCreateAdapter(PreferenceScreen preferenceScreen) {
        return new PreferenceGroupAdapter(preferenceScreen) {
            @Override
            @NonNull
            public PreferenceViewHolder onCreateViewHolder(@NonNull ViewGroup parent,
                    int viewType) {
                PreferenceViewHolder vh = super.onCreateViewHolder(parent, viewType);
                vh.itemView.setStateListAnimator(AnimatorInflater.loadStateListAnimator(
                        getContext(), R.animator.preference));
                vh.itemView.setOnTouchListener((v, e) -> {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN
                            && isPrimaryKey(e.getButtonState())) {
                        vh.itemView.requestFocus();
                        v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                                KeyEvent.KEYCODE_DPAD_CENTER));
                        return true;
                    } else if (e.getActionMasked() == MotionEvent.ACTION_UP
                            && isPrimaryKey(e.getButtonState())) {
                        v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                                KeyEvent.KEYCODE_DPAD_CENTER));
                        return true;
                    }
                    return false;
                });
                vh.itemView.setFocusable(true);
                vh.itemView.setFocusableInTouchMode(true);
                return vh;
            }
        };
    }

    @Override
    public void setPreferenceScreen(PreferenceScreen preferenceScreen) {
        mLifecycle.setPreferenceScreen(preferenceScreen);
        super.setPreferenceScreen(preferenceScreen);
    }

    @CallSuper
    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mLifecycle.onSaveInstanceState(outState);
    }

    @CallSuper
    @Override
    public void onStart() {
        mLifecycle.handleLifecycleEvent(ON_START);
        super.onStart();
    }

    @CallSuper
    @Override
    public void onResume() {
        super.onResume();
        mLifecycle.handleLifecycleEvent(ON_RESUME);
    }

    // This should only be invoked if the parent Fragment is TwoPanelSettingsFragment.
    @CallSuper
    @Override
    public void onArriveAtMainPanel(boolean forward) {
        logPageFocused(getPageId(), forward);
    }

    @CallSuper
    @Override
    public void onPause() {
        mLifecycle.handleLifecycleEvent(ON_PAUSE);
        super.onPause();
    }

    @CallSuper
    @Override
    public void onStop() {
        mLifecycle.handleLifecycleEvent(ON_STOP);
        super.onStop();
    }

    @CallSuper
    @Override
    public void onDestroy() {
        mLifecycle.handleLifecycleEvent(ON_DESTROY);
        super.onDestroy();
    }

    @CallSuper
    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        mLifecycle.onCreateOptionsMenu(menu, inflater);
        super.onCreateOptionsMenu(menu, inflater);
    }

    @CallSuper
    @Override
    public void onPrepareOptionsMenu(final Menu menu) {
        mLifecycle.onPrepareOptionsMenu(menu);
        super.onPrepareOptionsMenu(menu);
    }

    @CallSuper
    @Override
    public boolean onOptionsItemSelected(final MenuItem menuItem) {
        boolean lifecycleHandled = mLifecycle.onOptionsItemSelected(menuItem);
        if (!lifecycleHandled) {
            return super.onOptionsItemSelected(menuItem);
        }
        return lifecycleHandled;
    }

    /** Subclasses should override this to use their own PageId for statsd logging. */
    protected int getPageId() {
        return TvSettingsEnums.PAGE_CLASSIC_DEFAULT;
    }

    // check if such motion event should translate to key event DPAD_CENTER
    private boolean isPrimaryKey(int buttonState) {
        return buttonState == MotionEvent.BUTTON_PRIMARY
                || buttonState == MotionEvent.BUTTON_STYLUS_PRIMARY
                || buttonState == 0;  // motion events which creates by UI Automator
    }
}
