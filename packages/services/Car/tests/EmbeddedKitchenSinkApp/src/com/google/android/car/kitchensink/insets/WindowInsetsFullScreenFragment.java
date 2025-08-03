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

package com.google.android.car.kitchensink.insets;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

/**
 * Test fragment showing the transition to a full screen activity {@link WindowInsetsTestActivity}.
 *
 * The current fragment {@link WindowInsetsFullScreenFragment} and the activity
 * {@link WindowInsetsTestActivity} has a view defined at the center of the screen to show the UX
 * transition to/from full screen activity for fast tap cases.
 */
public final class WindowInsetsFullScreenFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable  ViewGroup container,
            @Nullable  Bundle savedInstanceState) {
        return inflater.inflate(R.layout.toggle_fullscreen_fragment, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Button showFullScrnBtn = view.requireViewById(R.id.show_full_screen_button);
        showFullScrnBtn.setOnClickListener(l -> navToFullScreen());
    }

    private void navToFullScreen() {
        Intent intent = new Intent(getActivity(), WindowInsetsTestActivity.class);
        startActivity(intent);
    }
}
