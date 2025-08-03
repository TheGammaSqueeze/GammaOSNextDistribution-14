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

package com.android.tv.twopanelsettings;

import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_PREFERENCE_INFO_SUMMARY;
import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_PREFERENCE_INFO_TEXT;
import static com.android.tv.twopanelsettings.slices.SlicesConstants.EXTRA_PREFERENCE_INFO_TITLE_ICON;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.drawable.Icon;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.TextUtils;
import android.transition.Fade;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.view.animation.AnimationUtils;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.leanback.app.GuidedStepSupportFragment;
import androidx.leanback.preference.LeanbackListPreferenceDialogFragmentCompat;
import androidx.leanback.preference.LeanbackPreferenceFragmentCompat;
import androidx.leanback.widget.OnChildViewHolderSelectedListener;
import androidx.leanback.widget.VerticalGridView;
import androidx.preference.ListPreference;
import androidx.preference.MultiSelectListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroupAdapter;
import androidx.preference.PreferenceViewHolder;
import androidx.recyclerview.widget.RecyclerView;

import com.android.tv.twopanelsettings.slices.CustomContentDescriptionPreference;
import com.android.tv.twopanelsettings.slices.HasCustomContentDescription;
import com.android.tv.twopanelsettings.slices.HasSliceUri;
import com.android.tv.twopanelsettings.slices.InfoFragment;
import com.android.tv.twopanelsettings.slices.SliceFragment;
import com.android.tv.twopanelsettings.slices.SlicePreference;
import com.android.tv.twopanelsettings.slices.SliceSeekbarPreference;
import com.android.tv.twopanelsettings.slices.SliceSwitchPreference;
import com.android.tv.twopanelsettings.slices.SlicesConstants;

import java.util.Set;

/**
 * This fragment provides containers for displaying two {@link LeanbackPreferenceFragmentCompat}.
 * The preference fragment on the left works as a main panel on which the user can operate.
 * The preference fragment on the right works as a preview panel for displaying the preview
 * information.
 */
public abstract class TwoPanelSettingsFragment extends Fragment implements
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
        PreferenceFragmentCompat.OnPreferenceStartScreenCallback,
        PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback {
    private static final String TAG = "TwoPanelSettingsFragment";
    private static final boolean DEBUG = false;
    private static final String PREVIEW_FRAGMENT_TAG =
            "com.android.tv.settings.TwoPanelSettingsFragment.PREVIEW_FRAGMENT";
    private static final String PREFERENCE_FRAGMENT_TAG =
            "com.android.tv.settings.TwoPanelSettingsFragment.PREFERENCE_FRAGMENT";
    private static final String EXTRA_PREF_PANEL_IDX =
            "com.android.tv.twopanelsettings.PREF_PANEL_IDX";
    private static final int[] frameResIds =
            {R.id.frame1, R.id.frame2, R.id.frame3, R.id.frame4, R.id.frame5, R.id.frame6,
                    R.id.frame7, R.id.frame8, R.id.frame9, R.id.frame10};

    private static final long PANEL_ANIMATION_SLIDE_MS = 1000;
    private static final long PANEL_ANIMATION_ALPHA_MS = 200;
    private static final long PANEL_BACKGROUND_ANIMATION_ALPHA_MS = 500;
    private static final long PANEL_ANIMATION_DELAY_MS = 200;
    private static final long PREVIEW_PANEL_DEFAULT_DELAY_MS =
            ActivityManager.isLowRamDeviceStatic() ? 100 : 0;
    private static final boolean DEFAULT_CHECK_SCROLL_STATE =
            ActivityManager.isLowRamDeviceStatic();
    private static final long CHECK_IDLE_STATE_MS = 100;
    private PostShowPreviewRunnable mPostShowPreviewRunnable;
    private long mPreviewPanelCreationDelay = 0;
    private static final float PREVIEW_PANEL_ALPHA = 0.6f;

    private int mMaxScrollX;
    private final RootViewOnKeyListener mRootViewOnKeyListener = new RootViewOnKeyListener();
    private int mPrefPanelIdx;
    private HorizontalScrollView mScrollView;
    private Handler mHandler;
    private boolean mIsNavigatingBack;
    private boolean mCheckVerticalGridViewScrollState;
    private Preference mFocusedPreference;
    private boolean mIsWaitingForUpdatingPreview = false;
    private AudioManager mAudioManager;

    private static final String DELAY_MS = "delay_ms";
    private static final String CHECK_SCROLL_STATE = "check_scroll_state";

    /** An broadcast receiver to help OEM test best delay for preview panel fragment creation. */
    private final BroadcastReceiver mPreviewPanelDelayReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long delay = intent.getLongExtra(DELAY_MS, PREVIEW_PANEL_DEFAULT_DELAY_MS);
            boolean checkScrollState = intent.getBooleanExtra(
                    CHECK_SCROLL_STATE, DEFAULT_CHECK_SCROLL_STATE);
            Log.d(TAG, "New delay for creating preview panel fragment " + delay
                    + " check scroll state " + checkScrollState);
            mPreviewPanelCreationDelay = delay;
            mCheckVerticalGridViewScrollState = checkScrollState;
        }
    };


    private final OnGlobalLayoutListener mOnGlobalLayoutListener = new OnGlobalLayoutListener() {
        @Override
        public void onGlobalLayout() {
            if (getView() != null && getView().getViewTreeObserver() != null) {
                getView().getViewTreeObserver().removeOnGlobalLayoutListener(
                        mOnGlobalLayoutListener);
                moveToPanel(mPrefPanelIdx, false);
            }
        }
    };

    private class OnChildViewHolderSelectedListenerTwoPanel extends
            OnChildViewHolderSelectedListener {
        private final int mPaneLIndex;

        OnChildViewHolderSelectedListenerTwoPanel(int panelIndex) {
            mPaneLIndex = panelIndex;
        }

        @Override
        public void onChildViewHolderSelected(RecyclerView parent,
                RecyclerView.ViewHolder child, int position, int subposition) {
            if (parent == null || child == null) {
                return;
            }
            int adapterPosition = child.getAdapterPosition();
            PreferenceGroupAdapter preferenceGroupAdapter =
                    (PreferenceGroupAdapter) parent.getAdapter();
            if (preferenceGroupAdapter != null) {
                Preference preference = preferenceGroupAdapter.getItem(adapterPosition);
                onPreferenceFocused(preference, mPaneLIndex);
            }
        }

        @Override
        public void onChildViewHolderSelectedAndPositioned(RecyclerView parent,
                RecyclerView.ViewHolder child, int position, int subposition) {
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mCheckVerticalGridViewScrollState = getContext().getResources()
                .getBoolean(R.bool.config_check_scroll_state);
        mPreviewPanelCreationDelay = getContext().getResources()
                .getInteger(R.integer.config_preview_panel_create_delay);

        updatePreviewPanelCreationDelayForLowRamDevice();
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
    }

    private void updatePreviewPanelCreationDelayForLowRamDevice() {
        if (ActivityManager.isLowRamDeviceStatic() && mPreviewPanelCreationDelay == 0) {
            mPreviewPanelCreationDelay = PREVIEW_PANEL_DEFAULT_DELAY_MS;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.two_panel_settings_fragment, container, false);
        mScrollView = v.findViewById(R.id.scrollview);
        mHandler = new Handler();
        if (savedInstanceState != null) {
            mPrefPanelIdx = savedInstanceState.getInt(EXTRA_PREF_PANEL_IDX, mPrefPanelIdx);
            // Move to correct panel once global layout finishes.
            v.getViewTreeObserver().addOnGlobalLayoutListener(mOnGlobalLayoutListener);
        }
        mMaxScrollX = computeMaxRightScroll();
        return v;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putInt(EXTRA_PREF_PANEL_IDX, mPrefPanelIdx);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        if (savedInstanceState == null) {
            onPreferenceStartInitialScreen();
        }
    }

    /** Extend this method to provide the initial screen **/
    public abstract void onPreferenceStartInitialScreen();

    private boolean isPreferenceFragment(String fragment) {
        try {
            return LeanbackPreferenceFragmentCompat.class.isAssignableFrom(Class.forName(fragment));
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Fragment class not found " + e);
            return false;
        }
    }

    private boolean isInfoFragment(String fragment) {
        try {
            return InfoFragment.class.isAssignableFrom(Class.forName(fragment));
        } catch (ClassNotFoundException e) {
            Log.e(TAG, "Fragment class not found " + e);
            return false;
        }
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, Preference pref) {
        if (pref == null) {
            return false;
        }
        if (DEBUG) {
            Log.d(TAG, "onPreferenceStartFragment " + pref.getTitle());
        }
        if (pref.getFragment() == null) {
            return false;
        }
        Fragment preview = getChildFragmentManager().findFragmentById(
                frameResIds[mPrefPanelIdx + 1]);
        if (preview != null && !(preview instanceof DummyFragment)) {
            if (!(preview instanceof InfoFragment)) {
                if (!mIsWaitingForUpdatingPreview) {
                    navigateToPreviewFragment();
                }
            }
        } else {
            // If there is no corresponding slice provider, thus the corresponding fragment is not
            // created, return true so it won't be handled by onPreferenceTreeClick in
            // PreferenceFragment, but instead onPreferenceFocused will handle it.
            if (pref instanceof SlicePreference) {
                return true;
            }
            try {
                Fragment fragment = Fragment.instantiate(getActivity(), pref.getFragment(),
                        pref.getExtras());
                if (fragment instanceof GuidedStepSupportFragment) {
                    startImmersiveFragment(fragment);
                } else {
                    if (DEBUG) {
                        Log.d(TAG, "No-op: Preference is clicked before preview is shown");
                    }
                    // return true so it won't be handled by onPreferenceTreeClick
                    // in PreferenceFragment
                    return true;
                }
            } catch (Exception e) {
                Log.e(TAG, "error trying to instantiate fragment " + e);
                // return true so it won't be handled by onPreferenceTreeClick in PreferenceFragment
                return true;
            }
        }
        return true;
    }

    /** Navigate back to the previous fragment **/
    public void navigateBack() {
        back(false);
    }

    /** Navigate into current preview fragment */
    public void navigateToPreviewFragment() {
        Fragment previewFragment = getChildFragmentManager().findFragmentById(
                frameResIds[mPrefPanelIdx + 1]);
        if (previewFragment instanceof NavigationCallback) {
            ((NavigationCallback) previewFragment).onNavigateToPreview();
        }
        if (previewFragment == null || previewFragment instanceof DummyFragment) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "navigateToPreviewFragment");
        }
        if (mPrefPanelIdx + 1 >= frameResIds.length) {
            Log.w(TAG, "Maximum level of depth reached.");
            return;
        }
        Fragment initialPreviewFragment = getInitialPreviewFragment(previewFragment);
        if (initialPreviewFragment == null) {
            initialPreviewFragment = new DummyFragment();
        }
        initialPreviewFragment.setExitTransition(null);

        if (previewFragment.getView() != null) {
            previewFragment.getView().setImportantForAccessibility(
                    View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }

        mPrefPanelIdx++;

        Fragment fragmentToBeMainPanel = getChildFragmentManager()
                .findFragmentById(frameResIds[mPrefPanelIdx]);
        addOrRemovePreferenceFocusedListener(fragmentToBeMainPanel, true);
        final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.replace(frameResIds[mPrefPanelIdx + 1], initialPreviewFragment,
                PREVIEW_FRAGMENT_TAG);
        transaction.commitAllowingStateLoss();

        moveToPanel(mPrefPanelIdx, true);
        removeFragmentAndAddToBackStack(mPrefPanelIdx - 1);
    }

    private boolean isA11yOn() {
        if (getActivity() == null) {
            return false;
        }
        return Settings.Secure.getInt(
                getActivity().getContentResolver(),
                Settings.Secure.ACCESSIBILITY_ENABLED, 0) == 1;
    }

    private void updateAccessibilityTitle(Fragment fragment) {
        CharSequence newA11yTitle = "";
        if (fragment instanceof SliceFragment) {
            newA11yTitle = ((SliceFragment) fragment).getScreenTitle();
        } else if (fragment instanceof LeanbackPreferenceFragmentCompat) {
            newA11yTitle = ((LeanbackPreferenceFragmentCompat) fragment).getPreferenceScreen()
                    .getTitle();
        } else if (fragment instanceof GuidedStepSupportFragment) {
            if (fragment.getView() != null) {
                View titleView = fragment.getView().findViewById(R.id.guidance_title);
                if (titleView instanceof TextView) {
                    newA11yTitle = ((TextView) titleView).getText();
                }
            }
        }

        if (!TextUtils.isEmpty(newA11yTitle)) {
            if (DEBUG) {
                Log.d(TAG, "changing a11y title to: " + newA11yTitle);
            }

            // Set both window title and pane title to avoid messy announcements when coming from
            // other activities. (window title is announced on activity change)
            getActivity().getWindow().setTitle(newA11yTitle);
            if (getView() != null
                    && getView().findViewById(R.id.two_panel_fragment_container) != null) {
                getView().findViewById(R.id.two_panel_fragment_container)
                        .setAccessibilityPaneTitle(newA11yTitle);
            }
        }
    }

    private void addOrRemovePreferenceFocusedListener(Fragment fragment, boolean isAddingListener) {
        if (!(fragment instanceof LeanbackPreferenceFragmentCompat)) {
            return;
        }
        LeanbackPreferenceFragmentCompat leanbackPreferenceFragment =
                (LeanbackPreferenceFragmentCompat) fragment;
        VerticalGridView listView = (VerticalGridView) leanbackPreferenceFragment.getListView();
        if (listView != null) {
            listView.setOnChildViewHolderSelectedListener(
                    isAddingListener
                            ? new OnChildViewHolderSelectedListenerTwoPanel(mPrefPanelIdx)
                            : null);
            if (!isAddingListener) {
                mHandler.removeCallbacks(mPostShowPreviewRunnable);
            }
        }
    }

    /**
     * Displays left panel preference fragment to the user.
     *
     * @param fragment Fragment instance to be added.
     */
    public void startPreferenceFragment(@NonNull Fragment fragment) {
        if (DEBUG) {
            Log.d(TAG, "startPreferenceFragment");
        }
        addOrRemovePreferenceFocusedListener(fragment, true);
        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.add(frameResIds[mPrefPanelIdx], fragment, PREFERENCE_FRAGMENT_TAG);
        transaction.commitNowAllowingStateLoss();

        Fragment initialPreviewFragment = getInitialPreviewFragment(fragment);
        if (initialPreviewFragment == null) {
            initialPreviewFragment = new DummyFragment();
        }
        initialPreviewFragment.setExitTransition(null);

        transaction = getChildFragmentManager().beginTransaction();
        transaction.add(frameResIds[mPrefPanelIdx + 1], initialPreviewFragment,
                initialPreviewFragment.getClass().toString());
        transaction.commitAllowingStateLoss();
    }

    @Override
    public boolean onPreferenceDisplayDialog(
            @NonNull PreferenceFragmentCompat caller, Preference pref) {
        if (pref == null) {
            return false;
        }
        if (DEBUG) {
            Log.d(TAG, "PreferenceDisplayDialog");
        }
        if (caller == null) {
            throw new IllegalArgumentException("Cannot display dialog for preference " + pref
                    + ", Caller must not be null!");
        }
        Fragment preview = getChildFragmentManager().findFragmentById(
                frameResIds[mPrefPanelIdx + 1]);
        if (preview != null && !(preview instanceof DummyFragment)) {
            if (preview instanceof NavigationCallback) {
                ((NavigationCallback) preview).onNavigateToPreview();
            }
            mPrefPanelIdx++;
            moveToPanel(mPrefPanelIdx, true);
            removeFragmentAndAddToBackStack(mPrefPanelIdx - 1);
            return true;
        }
        return false;
    }

    private boolean equalArguments(Bundle a, Bundle b) {
        if (a == null && b == null) {
            return true;
        }
        if (a == null || b == null) {
            return false;
        }
        Set<String> aks = a.keySet();
        Set<String> bks = b.keySet();
        if (a.size() != b.size()) {
            return false;
        }
        if (!aks.containsAll(bks)) {
            return false;
        }
        for (String key : aks) {
            if (a.get(key) == null && b.get(key) == null) {
                continue;
            }
            if (a.get(key) == null || b.get(key) == null) {
                return false;
            }
            if (a.get(key) instanceof Icon && b.get(key) instanceof Icon) {
                if (!((Icon) a.get(key)).sameAs((Icon) b.get(key))) {
                    return false;
                }
            } else if (!a.get(key).equals(b.get(key))) {
                return false;
            }
        }
        return true;
    }

    /** Callback from SliceFragment **/
    public interface SliceFragmentCallback {
        /** Triggered when preference is focused **/
        void onPreferenceFocused(Preference preference);

        /** Triggered when Seekbar preference is changed **/
        void onSeekbarPreferenceChanged(SliceSeekbarPreference preference, int addValue);
    }

    protected void onPreferenceFocused(Preference pref, int panelIndex) {
        onPreferenceFocusedImpl(pref, false, panelIndex);
    }

    private void onPreferenceFocusedImpl(Preference pref, boolean forceRefresh, int panelIndex) {
        if (pref == null) {
            return;
        }
        if (DEBUG) {
            Log.d(TAG, "onPreferenceFocused " + pref.getTitle());
        }
        final Fragment prefFragment =
                getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx]);
        if (prefFragment instanceof SliceFragmentCallback) {
            ((SliceFragmentCallback) prefFragment).onPreferenceFocused(pref);
        }
        mFocusedPreference = pref;
        if (mCheckVerticalGridViewScrollState || mPreviewPanelCreationDelay > 0) {
            mIsWaitingForUpdatingPreview = true;
            VerticalGridView listView = (VerticalGridView)
                    ((LeanbackPreferenceFragmentCompat) prefFragment).getListView();
            if (mPostShowPreviewRunnable == null) {
                mPostShowPreviewRunnable = new PostShowPreviewRunnable();
            }
            mHandler.removeCallbacks(mPostShowPreviewRunnable);
            mPostShowPreviewRunnable.updatePreviewInfo(listView, pref, forceRefresh, panelIndex);
            mHandler.postDelayed(mPostShowPreviewRunnable, mPreviewPanelCreationDelay);
        } else {
            handleFragmentTransactionWhenFocused(pref, forceRefresh, panelIndex);
        }
    }

    private final class PostShowPreviewRunnable implements Runnable {
        private VerticalGridView mListView;
        private Preference mPref;
        private boolean mForceFresh;
        private int mPanelIndex;

        PostShowPreviewRunnable() {}

        public void updatePreviewInfo(
                VerticalGridView listView, Preference pref, boolean forceFresh, int panelIndex) {
            this.mListView = listView;
            this.mPref = pref;
            this.mForceFresh = forceFresh;
            mPanelIndex = panelIndex;
        }

        @Override
        public void run() {
            if (mPref == mFocusedPreference) {
                if (mListView != null
                        && mListView.getScrollState() != RecyclerView.SCROLL_STATE_IDLE) {
                    mHandler.postDelayed(this, CHECK_IDLE_STATE_MS);
                } else {
                    handleFragmentTransactionWhenFocused(mPref, mForceFresh, mPanelIndex);
                    mIsWaitingForUpdatingPreview = false;
                }
            }
        }
    }

    private void handleFragmentTransactionWhenFocused(Preference pref, boolean forceRefresh,
            int panelIndex) {
        if (!isAdded() || panelIndex != mPrefPanelIdx) {
            return;
        }
        Fragment previewFragment = null;
        final Fragment prefFragment =
                getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx]);
        try {
            previewFragment = onCreatePreviewFragment(prefFragment, pref);
        } catch (Exception e) {
            Log.w(TAG, "Cannot instantiate the fragment from preference: " + pref, e);
        }
        if (previewFragment == null) {
            previewFragment = new DummyFragment();
        }
        final Fragment existingPreviewFragment =
                getChildFragmentManager().findFragmentById(
                        frameResIds[mPrefPanelIdx + 1]);
        if (existingPreviewFragment != null
                && existingPreviewFragment.getClass().equals(previewFragment.getClass())
                && equalArguments(existingPreviewFragment.getArguments(),
                previewFragment.getArguments())) {
            if (isRTL() && mScrollView.getScrollX() == 0 && mPrefPanelIdx == 0
                    && getView() != null && getView().getViewTreeObserver() != null) {
                // For RTL we need to reclaim focus to the correct scroll position if a pref
                // launches a new activity because the horizontal scroll goes back to 0.
                getView().getViewTreeObserver().addOnGlobalLayoutListener(
                        mOnGlobalLayoutListener);
            }
            if (!forceRefresh) {
                return;
            }
        }

        // If the existing preview fragment is recreated when the activity is recreated, the
        // animation would fall back to "slide left", in this case, we need to set the exit
        // transition.
        if (existingPreviewFragment != null) {
            existingPreviewFragment.setExitTransition(null);
        }
        previewFragment.setEnterTransition(new Fade());
        previewFragment.setExitTransition(null);
        final FragmentTransaction transaction =
                getChildFragmentManager().beginTransaction();
        transaction.setCustomAnimations(R.animator.fade_in_preview_panel,
                R.animator.fade_out_preview_panel);
        transaction.replace(frameResIds[mPrefPanelIdx + 1], previewFragment);
        transaction.commitNowAllowingStateLoss();

        // Some fragments may steal focus on creation. Reclaim focus on main fragment.
        if (getView() != null && getView().getViewTreeObserver() != null) {
            getView().getViewTreeObserver().addOnGlobalLayoutListener(
                    mOnGlobalLayoutListener);
        }
    }

    private boolean onSeekbarPreferenceChanged(SliceSeekbarPreference pref, int addValue) {
        final Fragment prefFragment =
                getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx]);
        if (prefFragment instanceof SliceFragmentCallback) {
            ((SliceFragmentCallback) prefFragment).onSeekbarPreferenceChanged(pref, addValue);
        }
        return true;
    }

    private boolean isRTL() {
        return getResources().getConfiguration().getLayoutDirection() == View.LAYOUT_DIRECTION_RTL;
    }

    @Override
    public void onResume() {
        if (DEBUG) {
            Log.d(TAG, "onResume");
        }
        super.onResume();
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction("com.android.tv.settings.PREVIEW_DELAY");
        getContext().registerReceiver(mPreviewPanelDelayReceiver, intentFilter,
                Context.RECEIVER_EXPORTED_UNAUDITED);
        // Trap back button presses
        final TwoPanelSettingsRootView rootView = (TwoPanelSettingsRootView) getView();
        if (rootView != null) {
            rootView.setOnBackKeyListener(mRootViewOnKeyListener);
        }
    }

    @Override
    public void onPause() {
        if (DEBUG) {
            Log.d(TAG, "onPause");
        }
        super.onPause();
        getContext().unregisterReceiver(mPreviewPanelDelayReceiver);
        final TwoPanelSettingsRootView rootView = (TwoPanelSettingsRootView) getView();
        if (rootView != null) {
            rootView.setOnBackKeyListener(null);
        }
    }

    /**
     * Displays a fragment to the user, temporarily replacing the contents of this fragment.
     *
     * @param fragment Fragment instance to be added.
     */
    public void startImmersiveFragment(@NonNull Fragment fragment) {
        if (DEBUG) {
            Log.d(TAG, "Starting immersive fragment.");
        }
        addOrRemovePreferenceFocusedListener(fragment, true);
        final FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        Fragment target = getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx]);
        fragment.setTargetFragment(target, 0);
        transaction
                .add(R.id.two_panel_fragment_container, fragment)
                .remove(target)
                .addToBackStack(null)
                .commitAllowingStateLoss();
        mHandler.post(() -> {
            updateAccessibilityTitle(fragment);
        });

    }

    public static class DummyFragment extends Fragment {
        @Override
        public @Nullable
        View onCreateView(LayoutInflater inflater, ViewGroup container,
                Bundle savedInstanceState) {
            return inflater.inflate(R.layout.dummy_fragment, container, false);
        }
    }

    /**
     * Implement this if fragment needs to handle DPAD_LEFT & DPAD_RIGHT itself in some cases
     **/
    public interface NavigationCallback {

        /**
         * Returns true if the fragment is in the state that can navigate back on receiving a
         * navigation DPAD key. When true, TwoPanelSettings will initiate a back operation on
         * receiving a left key. This method doesn't apply to back key: back key always initiates a
         * back operation.
         */
        boolean canNavigateBackOnDPAD();

        /**
         * Callback when navigating to preview screen
         */
        void onNavigateToPreview();

        /**
         * Callback when returning to previous screen
         */
        void onNavigateBack();
    }

    /**
     * Implement this if the component (typically a Fragment) is preview-able and would like to get
     * some lifecycle-like callback(s) when the component becomes the main panel.
     */
    public interface PreviewableComponentCallback {

        /**
         * Lifecycle-like callback when the component becomes main panel from the preview panel. For
         * Fragment, this will be invoked right after the preview fragment sliding into the main
         * panel.
         *
         * @param forward means whether the component arrives at main panel when users are
         *                navigating forwards (deeper into the TvSettings tree).
         */
        void onArriveAtMainPanel(boolean forward);
    }

    private class RootViewOnKeyListener implements View.OnKeyListener {

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (!isAdded()) {
                Log.d(TAG, "Fragment not attached yet.");
                return true;
            }
            Fragment prefFragment =
                    getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx]);

            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT)) {
                Preference preference = getChosenPreference(prefFragment);
                if ((preference instanceof SliceSeekbarPreference)) {
                    SliceSeekbarPreference sbPref = (SliceSeekbarPreference) preference;
                    if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                        onSeekbarPreferenceChanged(sbPref, 1);
                    } else {
                        onSeekbarPreferenceChanged(sbPref, -1);
                    }
                    return true;
                }
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
                if (event.getRepeatCount() > 0) {
                    // Ignore long press on back button.
                    return false;
                }
                return back(true);
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && ((!isRTL() && keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
                    || (isRTL() && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT))) {
                if (prefFragment instanceof NavigationCallback
                        && !((NavigationCallback) prefFragment).canNavigateBackOnDPAD()) {
                    return false;
                }
                return back(false);
            }

            if (event.getAction() == KeyEvent.ACTION_DOWN
                    && ((!isRTL() && keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
                    || (isRTL() && keyCode == KeyEvent.KEYCODE_DPAD_LEFT))) {
                forward();
                // TODO(b/163432209): improve NavigationCallback and be more specific here.
                // Do not consume the KeyEvent for NavigationCallback classes such as date & time
                // picker.
                return !(prefFragment instanceof NavigationCallback);
            }
            return false;
        }
    }

    private void forward() {
        if (!isAdded()) {
            Log.d(TAG, "Fragment not attached yet.");
            return;
        }
        final TwoPanelSettingsRootView rootView = (TwoPanelSettingsRootView) getView();
        if (shouldPerformClick()) {
            rootView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,
                    KeyEvent.KEYCODE_DPAD_CENTER));
            rootView.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,
                    KeyEvent.KEYCODE_DPAD_CENTER));
        } else {
            Fragment previewFragment = getChildFragmentManager()
                    .findFragmentById(frameResIds[mPrefPanelIdx + 1]);
            if (!(previewFragment instanceof InfoFragment)
                    && !mIsWaitingForUpdatingPreview) {
                mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_RIGHT);
                navigateToPreviewFragment();
            }
        }
    }

    private boolean shouldPerformClick() {
        Fragment prefFragment =
                getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx]);
        Preference preference = getChosenPreference(prefFragment);
        if (preference == null) {
            return false;
        }
        // This is for the case when a preference has preview but once user navigate to
        // see the preview, settings actually launch an intent to start external activity.
        if (preference.getIntent() != null && !TextUtils.isEmpty(preference.getFragment())) {
            return true;
        }
        return preference instanceof SlicePreference
                && ((SlicePreference) preference).getSliceAction() != null
                && ((SlicePreference) preference).getUri() != null;
    }

    private boolean back(boolean isKeyBackPressed) {
        if (!isAdded()) {
            Log.d(TAG, "Fragment not attached yet.");
            return true;
        }
        if (mIsNavigatingBack) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (DEBUG) {
                        Log.d(TAG, "Navigating back is deferred.");
                    }
                    back(isKeyBackPressed);
                }
            }, PANEL_ANIMATION_DELAY_MS + PANEL_ANIMATION_DELAY_MS);
            return true;
        }
        if (DEBUG) {
            Log.d(TAG, "Going back one level.");
        }

        final Fragment immersiveFragment =
                getChildFragmentManager().findFragmentById(R.id.two_panel_fragment_container);
        if (immersiveFragment != null) {
            getChildFragmentManager().popBackStack();
            moveToPanel(mPrefPanelIdx, false);
            return true;
        }

        // When a11y is on, we allow InfoFragments to take focus without scrolling panels. So if
        // the user presses back button in this state, we should not scroll our panels back, or exit
        // Settings activity, but rather reinstate the focus to be on the main panel.
        Fragment preview =
                getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx + 1]);
        if (isA11yOn() && preview instanceof InfoFragment && preview.getView() != null
                && preview.getView().hasFocus()) {
            View mainPanelView = getChildFragmentManager()
                    .findFragmentById(frameResIds[mPrefPanelIdx]).getView();
            if (mainPanelView != null) {
                mainPanelView.requestFocus();
                return true;
            }
        }

        if (mPrefPanelIdx < 1) {
            // Disallow the user to use "dpad left" to finish activity in the first screen
            if (isKeyBackPressed) {
                getActivity().finish();
            }
            return true;
        }

        mIsNavigatingBack = true;
        getChildFragmentManager().popBackStack();

        mPrefPanelIdx--;

        mHandler.postDelayed(() -> {
            if (isKeyBackPressed) {
                mAudioManager.playSoundEffect(AudioManager.FX_BACK);
            } else {
                mAudioManager.playSoundEffect(AudioManager.FX_FOCUS_NAVIGATION_LEFT);
            }
            moveToPanel(mPrefPanelIdx, true);
        }, PANEL_ANIMATION_DELAY_MS);

        mHandler.postDelayed(() -> {
            removeFragment(mPrefPanelIdx + 2);
            mIsNavigatingBack = false;
            Fragment previewFragment =
                    getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx + 1]);
            if (previewFragment instanceof NavigationCallback) {
                ((NavigationCallback) previewFragment).onNavigateBack();
            }
        }, PANEL_ANIMATION_DELAY_MS + PANEL_ANIMATION_DELAY_MS);
        return true;
    }

    private void removeFragment(int index) {
        Fragment fragment = getChildFragmentManager().findFragmentById(frameResIds[index]);
        if (fragment != null) {
            getChildFragmentManager().beginTransaction().remove(fragment).commitAllowingStateLoss();
        }
    }

    private void removeFragmentAndAddToBackStack(int index) {
        if (index < 0) {
            return;
        }
        Fragment removePanel = getChildFragmentManager().findFragmentById(frameResIds[index]);
        if (removePanel != null) {
            removePanel.setExitTransition(new Fade());
            getChildFragmentManager().beginTransaction().remove(removePanel)
                    .addToBackStack("remove " + removePanel.getClass().getName())
                    .commitAllowingStateLoss();
        }
    }

    /** For RTL layout, we need to know the right edge from where the panels start scrolling. */
    private int computeMaxRightScroll() {
        int scrollViewWidth = getResources().getDimensionPixelSize(R.dimen.tp_settings_panes_width);
        int panelWidth = getResources().getDimensionPixelSize(
                R.dimen.tp_settings_preference_pane_width);
        int panelPadding = getResources().getDimensionPixelSize(
                R.dimen.preference_pane_extra_padding_start) * 2;
        int result = frameResIds.length * panelWidth - scrollViewWidth + panelPadding;
        return result < 0 ? 0 : result;
    }

    /** Scrolls such that the panel with given index is the main panel shown on the left. */
    private void moveToPanel(final int index, boolean smoothScroll) {
        mHandler.post(() -> {
            if (DEBUG) {
                Log.d(TAG, "Moving to panel " + index);
            }
            if (!isAdded()) {
                return;
            }
            Fragment fragmentToBecomeMainPanel =
                    getChildFragmentManager().findFragmentById(frameResIds[index]);
            Fragment fragmentToBecomePreviewPanel =
                    getChildFragmentManager().findFragmentById(frameResIds[index + 1]);
            // Positive value means that the panel is scrolling to right (navigate forward for LTR
            // or navigate backwards for RTL) and vice versa; 0 means that this is likely invoked
            // by GlobalLayoutListener and there's no actual sliding.
            int distanceToScrollToRight;
            int panelWidth = getResources().getDimensionPixelSize(
                    R.dimen.tp_settings_preference_pane_width);
            TwoPanelSettingsFrameLayout scrollToPanel = getView().findViewById(frameResIds[index]);
            TwoPanelSettingsFrameLayout previewPanel = getView().findViewById(
                    frameResIds[index + 1]);
            if (scrollToPanel == null || previewPanel == null) {
                return;
            }
            scrollToPanel.setOnDispatchTouchListener(null);
            previewPanel.setOnDispatchTouchListener((view, env) -> {
                if (env.getActionMasked() == MotionEvent.ACTION_UP) {
                    forward();
                }
                return true;
            });
            View scrollToPanelHead = scrollToPanel.findViewById(R.id.decor_title_container);
            View previewPanelHead = previewPanel.findViewById(R.id.decor_title_container);
            boolean scrollsToPreview =
                    isRTL() ? mScrollView.getScrollX() >= mMaxScrollX - panelWidth * index
                            : mScrollView.getScrollX() <= panelWidth * index;

            boolean setAlphaForPreview = fragmentToBecomePreviewPanel != null
                    && !(fragmentToBecomePreviewPanel instanceof DummyFragment)
                    && !(fragmentToBecomePreviewPanel instanceof InfoFragment);
            int previewPanelColor = getResources().getColor(
                    R.color.tp_preview_panel_background_color);
            int mainPanelColor = getResources().getColor(
                    R.color.tp_preference_panel_background_color);
            if (smoothScroll) {
                int animationEnd = isRTL() ? mMaxScrollX - panelWidth * index : panelWidth * index;
                distanceToScrollToRight = animationEnd - mScrollView.getScrollX();
                // Slide animation
                ObjectAnimator slideAnim = ObjectAnimator.ofInt(mScrollView, "scrollX",
                        mScrollView.getScrollX(), animationEnd);
                slideAnim.setAutoCancel(true);
                slideAnim.setDuration(PANEL_ANIMATION_SLIDE_MS);
                slideAnim.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (isA11yOn() && fragmentToBecomeMainPanel != null
                                && fragmentToBecomeMainPanel.getView() != null) {
                            fragmentToBecomeMainPanel.getView().requestFocus();
                        }
                    }
                });
                slideAnim.setInterpolator(AnimationUtils.loadInterpolator(
                        getContext(), R.anim.easing_browse));
                slideAnim.start();
                // Color animation
                if (scrollsToPreview) {
                    previewPanel.setAlpha(setAlphaForPreview ? PREVIEW_PANEL_ALPHA : 1f);
                    previewPanel.setBackgroundColor(previewPanelColor);
                    if (previewPanelHead != null) {
                        previewPanelHead.setBackgroundColor(previewPanelColor);
                    }
                    ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(scrollToPanel, "alpha",
                            scrollToPanel.getAlpha(), 1f);
                    ObjectAnimator backgroundColorAnim = ObjectAnimator.ofObject(scrollToPanel,
                            "backgroundColor",
                            new ArgbEvaluator(), previewPanelColor, mainPanelColor);
                    alphaAnim.setAutoCancel(true);
                    alphaAnim.setDuration(PANEL_ANIMATION_ALPHA_MS);
                    backgroundColorAnim.setAutoCancel(true);
                    backgroundColorAnim.setDuration(PANEL_BACKGROUND_ANIMATION_ALPHA_MS);
                    AnimatorSet animatorSet = new AnimatorSet();
                    if (scrollToPanelHead != null) {
                        ObjectAnimator backgroundColorAnimForHead = ObjectAnimator.ofObject(
                                scrollToPanelHead,
                                "backgroundColor",
                                new ArgbEvaluator(), previewPanelColor, mainPanelColor);
                        backgroundColorAnimForHead.setAutoCancel(true);
                        backgroundColorAnimForHead.setDuration(PANEL_BACKGROUND_ANIMATION_ALPHA_MS);
                        animatorSet.playTogether(alphaAnim, backgroundColorAnim,
                                backgroundColorAnimForHead);
                    } else {
                        animatorSet.playTogether(alphaAnim, backgroundColorAnim);
                    }
                    animatorSet.setInterpolator(AnimationUtils.loadInterpolator(
                            getContext(), R.anim.easing_browse));
                    animatorSet.start();
                } else {
                    scrollToPanel.setAlpha(1f);
                    scrollToPanel.setBackgroundColor(mainPanelColor);
                    if (scrollToPanelHead != null) {
                        scrollToPanelHead.setBackgroundColor(mainPanelColor);
                    }
                    ObjectAnimator alphaAnim = ObjectAnimator.ofFloat(previewPanel, "alpha",
                            previewPanel.getAlpha(), setAlphaForPreview ? PREVIEW_PANEL_ALPHA : 1f);
                    ObjectAnimator backgroundColorAnim = ObjectAnimator.ofObject(previewPanel,
                            "backgroundColor",
                            new ArgbEvaluator(), mainPanelColor, previewPanelColor);
                    alphaAnim.setAutoCancel(true);
                    alphaAnim.setDuration(PANEL_ANIMATION_ALPHA_MS);
                    backgroundColorAnim.setAutoCancel(true);
                    backgroundColorAnim.setDuration(PANEL_BACKGROUND_ANIMATION_ALPHA_MS);
                    AnimatorSet animatorSet = new AnimatorSet();
                    if (previewPanelHead != null) {
                        ObjectAnimator backgroundColorAnimForHead = ObjectAnimator.ofObject(
                                previewPanelHead,
                                "backgroundColor",
                                new ArgbEvaluator(), mainPanelColor, previewPanelColor);
                        backgroundColorAnimForHead.setAutoCancel(true);
                        backgroundColorAnimForHead.setDuration(PANEL_BACKGROUND_ANIMATION_ALPHA_MS);
                        animatorSet.playTogether(alphaAnim, backgroundColorAnim,
                                backgroundColorAnimForHead);
                    } else {
                        animatorSet.playTogether(alphaAnim, backgroundColorAnim);
                    }
                    animatorSet.setInterpolator(AnimationUtils.loadInterpolator(
                            getContext(), R.anim.easing_browse));
                    animatorSet.start();
                }
            } else {
                int scrollToX = isRTL() ? mMaxScrollX - panelWidth * index : panelWidth * index;
                distanceToScrollToRight = scrollToX - mScrollView.getScrollX();
                mScrollView.scrollTo(scrollToX, 0);
                previewPanel.setAlpha(setAlphaForPreview ? PREVIEW_PANEL_ALPHA : 1f);
                previewPanel.setBackgroundColor(previewPanelColor);
                if (previewPanelHead != null) {
                    previewPanelHead.setBackgroundColor(previewPanelColor);
                }
                scrollToPanel.setAlpha(1f);
                scrollToPanel.setBackgroundColor(mainPanelColor);
                if (scrollToPanelHead != null) {
                    scrollToPanelHead.setBackgroundColor(mainPanelColor);
                }
            }
            if (fragmentToBecomeMainPanel != null && fragmentToBecomeMainPanel.getView() != null) {
                if (!isA11yOn()) {
                    fragmentToBecomeMainPanel.getView().requestFocus();
                }
                for (int resId : frameResIds) {
                    Fragment f = getChildFragmentManager().findFragmentById(resId);
                    if (f != null) {
                        View view = f.getView();
                        if (view != null) {
                            view.setImportantForAccessibility(
                                    f == fragmentToBecomeMainPanel || f instanceof InfoFragment
                                            ? View.IMPORTANT_FOR_ACCESSIBILITY_YES
                                            : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                        }
                    }
                }
                if (fragmentToBecomeMainPanel instanceof PreviewableComponentCallback) {
                    if (distanceToScrollToRight > 0) {
                        ((PreviewableComponentCallback) fragmentToBecomeMainPanel)
                                .onArriveAtMainPanel(!isRTL());
                    } else if (distanceToScrollToRight < 0) {
                        ((PreviewableComponentCallback) fragmentToBecomeMainPanel)
                                .onArriveAtMainPanel(isRTL());
                    } // distanceToScrollToRight being 0 means no actual panel sliding; thus noop.
                }
                updateAccessibilityTitle(fragmentToBecomeMainPanel);
            }
        });
    }

    private Fragment getInitialPreviewFragment(Fragment fragment) {
        if (!(fragment instanceof LeanbackPreferenceFragmentCompat)) {
            return null;
        }

        LeanbackPreferenceFragmentCompat leanbackPreferenceFragment =
                (LeanbackPreferenceFragmentCompat) fragment;
        if (leanbackPreferenceFragment.getListView() == null) {
            return null;
        }

        VerticalGridView listView = (VerticalGridView) leanbackPreferenceFragment.getListView();
        int position = listView.getSelectedPosition();
        PreferenceGroupAdapter adapter =
                (PreferenceGroupAdapter) (leanbackPreferenceFragment.getListView().getAdapter());
        if (adapter == null) {
            return null;
        }
        Preference chosenPreference = adapter.getItem(position);
        // Find the first focusable preference if cannot find the selected preference
        if (chosenPreference == null || (listView.findViewHolderForPosition(position) != null
                && !listView.findViewHolderForPosition(position).itemView.hasFocusable())) {
            chosenPreference = null;
            for (int i = 0; i < listView.getChildCount(); i++) {
                View view = listView.getChildAt(i);
                if (view.hasFocusable()) {
                    PreferenceViewHolder viewHolder =
                            (PreferenceViewHolder) listView.getChildViewHolder(view);
                    chosenPreference = adapter.getItem(viewHolder.getAdapterPosition());
                    break;
                }
            }
        }

        if (chosenPreference == null) {
            return null;
        }
        return onCreatePreviewFragment(fragment, chosenPreference);
    }

    /**
     * Refocus the current selected preference. When a preference is selected and its InfoFragment
     * slice data changes. We need to call this method to make sure InfoFragment updates in time.
     * This is also helpful in refreshing preview of ListPreference.
     */
    public void refocusPreference(Fragment fragment) {
        if (!isFragmentInTheMainPanel(fragment)) {
            return;
        }
        Preference chosenPreference = getChosenPreference(fragment);
        try {
            if (chosenPreference != null) {
                if (chosenPreference.getFragment() != null
                        && InfoFragment.class.isAssignableFrom(
                        Class.forName(chosenPreference.getFragment()))) {
                    updateInfoFragmentStatus(fragment);
                }
                if (chosenPreference instanceof ListPreference) {
                    refocusPreferenceForceRefresh(chosenPreference, fragment);
                }
            }
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    /** Force refresh preview panel. */
    public void refocusPreferenceForceRefresh(Preference chosenPreference, Fragment fragment) {
        if (!isFragmentInTheMainPanel(fragment)) {
            return;
        }
        onPreferenceFocusedImpl(chosenPreference, true, mPrefPanelIdx);
    }

    /** Show error message in preview panel **/
    public void showErrorMessage(String errorMessage, Fragment fragment) {
        Fragment prefFragment =
                getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx]);
        if (fragment == prefFragment) {
            // If user has already navigated to the preview screen, main panel screen should be
            // updated to new InFoFragment. Create a fake preference to work around this case.
            Preference preference = new Preference(getContext());
            updatePreferenceWithErrorMessage(preference, errorMessage, getContext());
            Fragment newPrefFragment = onCreatePreviewFragment(null, preference);
            final FragmentTransaction transaction =
                    getChildFragmentManager().beginTransaction();
            transaction.setCustomAnimations(R.animator.fade_in_preview_panel,
                    R.animator.fade_out_preview_panel);
            transaction.replace(frameResIds[mPrefPanelIdx], newPrefFragment);
            transaction.commitAllowingStateLoss();
        } else {
            Preference preference = getChosenPreference(prefFragment);
            if (preference != null) {
                if (isA11yOn()) {
                    appendErrorToContentDescription(prefFragment, errorMessage);
                }
                updatePreferenceWithErrorMessage(preference, errorMessage, getContext());
                onPreferenceFocused(preference, mPrefPanelIdx);
            }
        }
    }

    private static void updatePreferenceWithErrorMessage(
            Preference preference, String errorMessage, Context context) {
        preference.setFragment(InfoFragment.class.getCanonicalName());
        Bundle b = preference.getExtras();
        b.putParcelable(EXTRA_PREFERENCE_INFO_TITLE_ICON,
                Icon.createWithResource(context, R.drawable.slice_error_icon));
        b.putCharSequence(EXTRA_PREFERENCE_INFO_TEXT,
                context.getString(R.string.status_unavailable));
        b.putCharSequence(EXTRA_PREFERENCE_INFO_SUMMARY, errorMessage);
    }

    private void appendErrorToContentDescription(Fragment fragment, String errorMessage) {
        Preference preference = getChosenPreference(fragment);

        String errorMessageContentDescription = "";
        if (preference.getTitle() != null) {
            errorMessageContentDescription += preference.getTitle().toString();
        }

        errorMessageContentDescription +=
                HasCustomContentDescription.CONTENT_DESCRIPTION_SEPARATOR
                        + getString(R.string.status_unavailable)
                        + HasCustomContentDescription.CONTENT_DESCRIPTION_SEPARATOR + errorMessage;

        if (preference instanceof SlicePreference) {
            ((SlicePreference) preference).setContentDescription(errorMessageContentDescription);
        } else if (preference instanceof SliceSwitchPreference) {
            ((SliceSwitchPreference) preference)
                    .setContentDescription(errorMessageContentDescription);
        } else if (preference instanceof CustomContentDescriptionPreference) {
            ((CustomContentDescriptionPreference) preference)
                    .setContentDescription(errorMessageContentDescription);
        }

        LeanbackPreferenceFragmentCompat leanbackPreferenceFragment =
                (LeanbackPreferenceFragmentCompat) fragment;
        if (leanbackPreferenceFragment.getListView() != null
                && leanbackPreferenceFragment.getListView().getAdapter() != null) {
            leanbackPreferenceFragment.getListView().getAdapter().notifyDataSetChanged();
        }
    }

    private void updateInfoFragmentStatus(Fragment fragment) {
        if (!isFragmentInTheMainPanel(fragment)) {
            return;
        }
        final Fragment existingPreviewFragment =
                getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx + 1]);
        if (existingPreviewFragment instanceof InfoFragment) {
            ((InfoFragment) existingPreviewFragment).updateInfoFragment();
        }
    }

    /** Get the current chosen preference. */
    public static Preference getChosenPreference(Fragment fragment) {
        if (!(fragment instanceof LeanbackPreferenceFragmentCompat)) {
            return null;
        }

        LeanbackPreferenceFragmentCompat leanbackPreferenceFragment =
                (LeanbackPreferenceFragmentCompat) fragment;
        if (leanbackPreferenceFragment.getListView() == null) {
            return null;
        }

        VerticalGridView listView = (VerticalGridView) leanbackPreferenceFragment.getListView();
        int position = listView.getSelectedPosition();
        PreferenceGroupAdapter adapter =
                (PreferenceGroupAdapter) (leanbackPreferenceFragment.getListView().getAdapter());
        return adapter != null ? adapter.getItem(position) : null;
    }

    /** Creates preview preference fragment. */
    public Fragment onCreatePreviewFragment(Fragment caller, Preference preference) {
        if (preference == null) {
            return null;
        }
        if (preference.getFragment() != null) {
            if (!isInfoFragment(preference.getFragment())
                    && !isPreferenceFragment(preference.getFragment())) {
                return null;
            }
            if (isPreferenceFragment(preference.getFragment())
                    && preference instanceof HasSliceUri) {
                HasSliceUri slicePref = (HasSliceUri) preference;
                if (slicePref.getUri() == null || !isUriValid(slicePref.getUri())) {
                    return null;
                }
                Bundle b = preference.getExtras();
                b.putString(SlicesConstants.TAG_TARGET_URI, slicePref.getUri());
                b.putCharSequence(SlicesConstants.TAG_SCREEN_TITLE, preference.getTitle());
            }
            return Fragment.instantiate(getActivity(), preference.getFragment(),
                    preference.getExtras());
        } else {
            Fragment f = null;
            if (preference instanceof ListPreference
                    && ((ListPreference) preference).getEntries() != null) {
                f = TwoPanelListPreferenceDialogFragment.newInstanceSingle(preference.getKey());
            } else if (preference instanceof MultiSelectListPreference
                    && ((MultiSelectListPreference) preference).getEntries() != null) {
                f = LeanbackListPreferenceDialogFragmentCompat.newInstanceMulti(
                        preference.getKey());
            }
            if (f != null && caller != null) {
                f.setTargetFragment(caller, 0);
            }
            return f;
        }
    }

    private boolean isUriValid(String uri) {
        if (uri == null) {
            return false;
        }
        ContentProviderClient client =
                getContext().getContentResolver().acquireContentProviderClient(Uri.parse(uri));
        if (client != null) {
            client.close();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Add focus listener to the child fragment. It must always be called after
     * the child fragment view is created since the listener is attached to the
     * {@link VerticalGridView} in the child fragment view.
     */
    public void addListenerForFragment(Fragment fragment) {
        if (isFragmentInTheMainPanel(fragment)) {
            addOrRemovePreferenceFocusedListener(fragment, true);
        }
    }

    /** Remove focus listener from the child fragment **/
    public void removeListenerForFragment(Fragment fragment) {
        addOrRemovePreferenceFocusedListener(fragment, false);
    }

    /** Check if fragment is in the main panel **/
    public boolean isFragmentInTheMainPanel(Fragment fragment) {
        return fragment == getChildFragmentManager().findFragmentById(frameResIds[mPrefPanelIdx]);
    }
}
