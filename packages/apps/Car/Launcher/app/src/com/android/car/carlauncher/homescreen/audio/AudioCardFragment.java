/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.car.carlauncher.homescreen.audio;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.car.carlauncher.R;
import com.android.car.carlauncher.homescreen.HomeCardFragment;
import com.android.car.carlauncher.homescreen.HomeCardInterface;
import com.android.car.carlauncher.homescreen.audio.dialer.DialerCardFragment;
import com.android.car.carlauncher.homescreen.audio.media.MediaCardFragment;

/**
 * Fragment used to display the audio related controls.
 */
public class AudioCardFragment extends Fragment implements HomeCardInterface.View {
    private View mRootView;

    private MediaCardFragment mMediaFragment;
    private DialerCardFragment mInCallFragment;
    private boolean mViewCreated;

    private HomeCardFragment.OnViewLifecycleChangeListener mOnViewLifecycleChangeListener;

    /**
     * Register a callback to be invoked when the fragment lifecycle changes.
     *
     * @param onViewLifecycleChangeListener The callback that will run
     */
    public void setOnViewLifecycleChangeListener(
            HomeCardFragment.OnViewLifecycleChangeListener onViewLifecycleChangeListener) {
        mOnViewLifecycleChangeListener = onViewLifecycleChangeListener;
        if (mViewCreated) {
            mOnViewLifecycleChangeListener.onViewCreated();
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.card_fragment_audio_card, container, false);
        return mRootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mViewCreated = true;
        mMediaFragment = new MediaCardFragment();
        mInCallFragment = new DialerCardFragment();

        FragmentTransaction ft = getChildFragmentManager().beginTransaction();
        ft.replace(R.id.media_fragment_container, mMediaFragment, mMediaFragment.getTag());
        ft.replace(R.id.in_call_fragment_container, mInCallFragment);
        ft.commitNow();

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction();
        transaction.hide(mInCallFragment);
        transaction.commit();

        if (mOnViewLifecycleChangeListener != null) {
            mOnViewLifecycleChangeListener.onViewCreated();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mViewCreated = false;
        if (mOnViewLifecycleChangeListener != null) {
            mOnViewLifecycleChangeListener.onViewDestroyed();
        }
    }

    @Override
    public Fragment getFragment() {
        return this;
    }

    @Override
    public void hideCard() {
        mRootView.setVisibility(View.GONE);
    }

    public MediaCardFragment getMediaFragment() {
        return mMediaFragment;
    }

    public DialerCardFragment getInCallFragment() {
        return mInCallFragment;
    }

    void showMediaCard() {
        FragmentManager fragmentManager = getChildFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.show(mMediaFragment);
        transaction.hide(mInCallFragment);
        transaction.commitAllowingStateLoss();
    }

    void showInCallCard() {
        FragmentManager fragmentManager = getChildFragmentManager();
        FragmentTransaction transaction = fragmentManager.beginTransaction();
        transaction.hide(mMediaFragment);
        transaction.show(mInCallFragment);
        transaction.commitAllowingStateLoss();
    }
}
