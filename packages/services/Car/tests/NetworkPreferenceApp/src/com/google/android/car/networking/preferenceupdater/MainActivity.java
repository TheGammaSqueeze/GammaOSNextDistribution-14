/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.google.android.car.networking.preferenceupdater;

import android.annotation.NonNull;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.google.android.car.networking.preferenceupdater.fragments.ManagerFragment;

import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends FragmentActivity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private final List<MenuEntry> mMenuEntries =
            new ArrayList<MenuEntry>() {
                {
                    add("Manager", ManagerFragment.class);
                    add("Quit", MainActivity.this::finish);
                }

                <T extends Fragment> void add(String text, Class<T> clazz) {
                    add(new FragmentMenuEntry(text, clazz));
                }

                void add(String text, ClickHandler onClick) {
                    add(new OnClickMenuEntry(text, onClick));
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);
        // Until we have more than one fragment, hide the selection button
        findViewById(R.id.manager_btn).setVisibility(View.INVISIBLE);

        mMenuEntries.get(0).onClick();

        findViewById(R.id.manager_btn).setOnClickListener((v) -> mMenuEntries.get(0).onClick());
        findViewById(R.id.exit_button_container)
                .setOnClickListener((v) -> mMenuEntries.get(1).onClick());
    }

    private interface ClickHandler {
        void onClick();
    }

    private abstract static class MenuEntry implements ClickHandler {
        abstract String getText();
    }

    private final class OnClickMenuEntry extends MenuEntry {
        private final String mText;
        private final ClickHandler mClickHandler;

        OnClickMenuEntry(String text, ClickHandler clickHandler) {
            mText = text;
            mClickHandler = clickHandler;
        }

        @Override
        String getText() {
            return mText;
        }

        @Override
        public void onClick() {
            mClickHandler.onClick();
        }
    }

    private final class FragmentMenuEntry<T extends Fragment> extends MenuEntry {
        private final class FragmentClassOrInstance<T extends Fragment> {
            final Class<T> mClazz;
            T mFragment = null;

            FragmentClassOrInstance(@NonNull final Class<T> clazz) {
                mClazz = clazz;
            }

            @NonNull
            T getFragment() {
                if (mFragment == null) {
                    try {
                        mFragment = mClazz.newInstance();
                    } catch (InstantiationException | IllegalAccessException e) {
                        Log.e(TAG, "unable to create fragment", e);
                    }
                }
                return mFragment;
            }
        }

        private final String mText;
        private final FragmentClassOrInstance<T> mFragment;

        FragmentMenuEntry(@NonNull String text, @NonNull Class<T> clazz) {
            mText = text;
            mFragment = new FragmentClassOrInstance<>(clazz);
        }

        @Override
        String getText() {
            return mText;
        }

        @Override
        public void onClick() {
            final Fragment fragment = mFragment.getFragment();
            if (fragment == null) {
                Log.e(TAG, "cannot show fragment for " + getText());
                return;
            }
            MainActivity.this.showFragment(fragment);
        }
    }

    private void showFragment(@NonNull Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.activity_content, fragment)
                .commit();
    }
}
