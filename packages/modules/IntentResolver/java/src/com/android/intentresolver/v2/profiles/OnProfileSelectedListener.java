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

package com.android.intentresolver.v2.profiles;

import androidx.viewpager.widget.ViewPager;

/** Listener interface for changes between the per-profile UI tabs. */
public interface OnProfileSelectedListener {
    /**
     * Callback for when the user changes the active tab.
     * <p>This callback is only called when the intent resolver or share sheet shows
     * more than one profile.
     *
     * @param profileId the ID of the newly-selected profile, e.g. {@link #PROFILE_PERSONAL}
     *                  if the personal profile tab was selected or {@link #PROFILE_WORK} if the
     *                  work profile tab
     *                  was selected.
     */
    void onProfilePageSelected(@MultiProfilePagerAdapter.ProfileType int profileId, int pageNumber);


    /**
     * Callback for when the scroll state changes. Useful for discovering when the user begins
     * dragging, when the pager is automatically settling to the current page, or when it is
     * fully stopped/idle.
     *
     * @param state {@link ViewPager#SCROLL_STATE_IDLE}, {@link ViewPager#SCROLL_STATE_DRAGGING}
     *              or {@link ViewPager#SCROLL_STATE_SETTLING}
     * @see ViewPager.OnPageChangeListener#onPageScrollStateChanged
     */
    void onProfilePageStateChanged(int state);
}
