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
package com.android.wallpaper.model;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.android.wallpaper.module.WallpaperPersister.Destination;
import com.android.wallpaper.module.WallpaperPersister.SetWallpaperCallback;

/**
 * {@link ViewModel} class that keeps track of the status of the "Set wallpaper" operation.
 */
public class SetWallpaperViewModel extends ViewModel {

    private static final String TAG = "SetWallpaperViewModel";

    public enum SetWallpaperStatus {
        UNKNOWN, PENDING, SUCCESS, ERROR
    }

    /**
     * @return a {@link SetWallpaperCallback} to set as listener for
     * {@link WallpaperSetter#setCurrentWallpaper} that will update the
     * {@link SetWallpaperViewModel} obtained from the given provider.
     */
    public static SetWallpaperCallback getCallback(@NonNull ViewModelProvider provider) {
        SetWallpaperViewModel viewModel = provider.get(SetWallpaperViewModel.class);
        return new SetWallpaperCallback() {
            @Override
            public void onSuccess(WallpaperInfo wallpaperInfo, @Destination int destination) {
                Log.d(TAG, "SetWallpaperCallback success");
                viewModel.mStatus.setValue(SetWallpaperStatus.SUCCESS);
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                Log.w(TAG, "SetWallpaperCallback error", throwable);
                viewModel.mStatus.setValue(SetWallpaperStatus.ERROR);
            }
        };
    }

    private final MutableLiveData<SetWallpaperStatus> mStatus = new MutableLiveData<>();

    @Destination
    private int mDestination;

    public SetWallpaperViewModel() {
        mStatus.setValue(SetWallpaperStatus.UNKNOWN);
    }

    public LiveData<SetWallpaperStatus> getStatus() {
        return mStatus;
    }

    public int getDestination() {
        return mDestination;
    }

    public void setDestination(int destination) {
        mDestination = destination;
    }
}
