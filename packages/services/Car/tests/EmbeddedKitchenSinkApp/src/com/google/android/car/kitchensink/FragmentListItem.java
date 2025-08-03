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

package com.google.android.car.kitchensink;

import static com.google.android.car.kitchensink.KitchenSinkActivity.TAG;

import android.util.Log;

import androidx.fragment.app.Fragment;

import com.android.car.ui.recyclerview.CarUiContentListItem;

import java.io.FileDescriptor;
import java.io.PrintWriter;

public final class FragmentListItem<T extends Fragment> extends CarUiContentListItem {

    private static final class FragmentClassOrInstance<T extends Fragment> {
        private final Class<T> mClazz;
        private T mFragment = null;

        FragmentClassOrInstance(Class<T> clazz) {
            mClazz = clazz;
        }

        T getFragment() {
            if (mFragment == null) {
                try {
                    mFragment = mClazz.getDeclaredConstructor().newInstance();
                } catch (Exception e) {
                    Log.e(TAG, "FragmentListItem: unable to create fragment", e);
                }
            }
            return mFragment;
        }
    }

    private final FragmentClassOrInstance<T> mFragment;
    private boolean mIsFavourite;

    public FragmentListItem(String text, boolean isFavourite, Class<T> clazz,
            OnClickListener listener) {
        super(Action.NONE);
        mIsFavourite = isFavourite;
        mFragment = new FragmentClassOrInstance<>(clazz);
        setTitle(text.toUpperCase());
        setOnItemClickedListener(listener);
    }

    public Fragment getFragment() {
        return mFragment.getFragment();
    }

    public boolean isFavourite() {
        return mIsFavourite;
    }

    public void toggleFavourite() {
        mIsFavourite = !mIsFavourite;
    }

    public void dump(String prefix, FileDescriptor fd, PrintWriter writer, String[] args) {
        Fragment fragment = mFragment.getFragment();
        if (fragment != null) {
            fragment.dump(prefix, fd, writer, args);
        } else {
            writer.printf("Cannot dump %s\n", getTitle());
        }
    }

}
