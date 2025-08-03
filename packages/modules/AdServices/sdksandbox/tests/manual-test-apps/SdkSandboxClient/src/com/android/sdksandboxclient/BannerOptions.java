/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.sdksandboxclient;

import android.content.SharedPreferences;

public class BannerOptions {

    public enum ViewType {
        RANDOM_COLOUR,
        INFLATED,
        VIDEO,
        WEBVIEW,
        AD_REFRESH,
        EDITTEXT
    }

    public enum OnClick {
        OPEN_CHROME,
        OPEN_PACKAGE,
    }

    public enum Placement {
        BOTTOM,
        SCROLL_VIEW
    }

    public enum AdSize {
        SMALL,
        MEDIUM,
        LARGE,
    };

    private final ViewType mViewType;
    private final String mVideoUrl;
    private final OnClick mOnClick;
    private final Placement mPlacement;
    private final AdSize mAdSize;

    private final String mPackageToOpen;

    public ViewType getViewType() {
        return mViewType;
    }

    public OnClick getOnClick() {
        return mOnClick;
    }

    public Placement getPlacement() {
        return mPlacement;
    }

    public String getVideoUrl() {
        return mVideoUrl;
    }

    public String getmPackageToOpen() {
        return mPackageToOpen;
    }

    public AdSize getAdSize() {
        return mAdSize;
    }

    @Override
    public String toString() {
        return String.format(
                "BannerOptions { ViewType=%s, VideoUrl=%s, OnClick=%s, Placement=%s AdSize=%s }",
                mViewType, mVideoUrl, mOnClick, mPlacement, mAdSize);
    }

    private BannerOptions(
            ViewType viewType,
            String videoUrl,
            OnClick onClick,
            String packageToOpen,
            Placement placement,
            AdSize adSize) {
        mViewType = viewType;
        mVideoUrl = videoUrl;
        mOnClick = onClick;
        mPlacement = placement;
        mPackageToOpen = packageToOpen;
        mAdSize = adSize;
    }

    public static BannerOptions fromSharedPreferences(SharedPreferences sharedPreferences) {
        return new BannerOptions(
                ViewType.valueOf(sharedPreferences.getString("banner_view_type", "")),
                sharedPreferences.getString("banner_video_url", ""),
                OnClick.valueOf(sharedPreferences.getString("banner_on_click", "")),
                sharedPreferences.getString("package_to_open", ""),
                Placement.valueOf(sharedPreferences.getString("banner_placement", "")),
                AdSize.valueOf(sharedPreferences.getString("banner_ad_size", "")));
    }
}
