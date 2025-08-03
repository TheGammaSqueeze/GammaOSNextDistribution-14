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

package com.google.android.tv.btservices;

import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.text.TextUtils;
import android.transition.Scene;
import android.transition.Slide;
import android.transition.Transition;
import android.transition.TransitionManager;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.ViewGroup;
import android.view.Window;
import java.util.HashMap;
import java.util.Map;

public class SettingsUtils {

    private static final String ACTION_CONNECT_INPUT_NORMAL =
            "com.google.android.intent.action.CONNECT_INPUT";
    private static final String INTENT_EXTRA_NO_INPUT_MODE = "no_input_mode";

    public static void sendPairingIntent(Context context, KeyEvent event) {
        // To be uncommented with enabling new pairing flow
        // Intent intent = new Intent(context, BluetoothScannerActivity.class);
        Intent intent = getPairingIntent();

        if (event != null) {
            intent.putExtra(INTENT_EXTRA_NO_INPUT_MODE, true)
                    .putExtra(Intent.EXTRA_KEY_EVENT, event);
        }
        context.startActivity(intent);
    }

    public static Intent getPairingIntent() {
        return new Intent(ACTION_CONNECT_INPUT_NORMAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    public static Map<String, String> getAudioManagerParameter(Context context, String param) {
        AudioManager am = context.getSystemService(AudioManager.class);
        String str = am.getParameters(param);
        Map<String, String> ret = new HashMap<>();
        if (TextUtils.isEmpty(param)) {
            return ret;
        }

        for (String s: str.split(";")) {
            String[] keyVal = s.split("=");
            ret.put(keyVal[0], keyVal[1]);
        }
        return ret;
    }

    public static class SettingsPanelAnimation {

        private final FragmentManager mFragmentManager;
        private final String mTag;
        private final FragmentFactory mFactory;
        private final ViewGroup mViewRoot;
        private final Window mWindow;

        public interface FragmentFactory {
            Fragment create();
        }

        public SettingsPanelAnimation(FragmentManager fm, String tag, ViewGroup root,
                FragmentFactory factory, Window window) {
            mFragmentManager = fm;
            mTag = tag;
            mFactory = factory;
            mViewRoot = root;
            mWindow = window;
        }

        public void transitionIn() {
            Fragment fragment = mFragmentManager.findFragmentByTag(mTag);
            final boolean replace = (fragment != null);
            fragment = mFactory.create();
            FragmentTransaction transact = mFragmentManager.beginTransaction();
            if (!replace) {
                transact.add(android.R.id.content, fragment, mTag);
            } else {
                transact.replace(android.R.id.content, fragment, mTag);
            }
            transact.commit();
            final Scene scene = new Scene(mViewRoot);
            final Slide slide = new Slide(Gravity.END);
            TransitionManager.go(scene, slide);
        }

        public void transitionOut(Runnable afterTransition) {
            final Fragment fragment = mFragmentManager.findFragmentByTag(mTag);
            if (fragment != null && fragment.isResumed()) {
                final Scene scene = new Scene(mViewRoot);
                scene.setEnterAction(() -> {
                    mFragmentManager.beginTransaction()
                            .remove(fragment)
                            .commitNow();
                });
                final Slide slide = new Slide(Gravity.END);

                slide.addListener(new Transition.TransitionListener() {
                    @Override
                    public void onTransitionStart(Transition transition) {
                        mWindow.setDimAmount(0);
                    }

                    @Override
                    public void onTransitionEnd(Transition transition) {
                        transition.removeListener(this);
                        if (afterTransition != null) {
                            afterTransition.run();
                        }
                    }

                    @Override
                    public void onTransitionCancel(Transition transition) {
                    }

                    @Override
                    public void onTransitionPause(Transition transition) {
                    }

                    @Override
                    public void onTransitionResume(Transition transition) {
                    }
                });
                TransitionManager.go(scene, slide);
            } else {
                if (afterTransition != null) {
                    afterTransition.run();
                }
            }
        }
    }
}
