/*
 * Copyright (C) 2019 The Android Open Source Project.
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

package com.android.car.themeplayground;

import android.os.Bundle;

import com.android.car.ui.recyclerview.CarUiRecyclerView;

import java.util.ArrayList;

/**
 * Activity that shows {@link CarUiRecyclerView} example with placeholder data.
 */
public class CarUiRecyclerViewSamples extends AbstractSampleActivity {

    private static final int DATA_TO_GENERATE = 15;

    private final ArrayList<String> mData = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Utils.onActivityCreateSetTheme(this);
        setContentView(R.layout.paged_recycler_view_samples);
        CarUiRecyclerView recyclerView = findViewById(R.id.list);
        RecyclerViewAdapter adapter = new RecyclerViewAdapter(generatePlaceholderData());
        recyclerView.setAdapter(adapter);
    }

    private ArrayList<String> generatePlaceholderData() {
        for (int i = 0; i <= DATA_TO_GENERATE; i++) {
            mData.add("data" + i);
        }
        return mData;
    }
}
