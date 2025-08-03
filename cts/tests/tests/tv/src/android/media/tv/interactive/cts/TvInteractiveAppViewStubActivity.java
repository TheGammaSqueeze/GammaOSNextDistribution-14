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

package android.media.tv.interactive.cts;

import android.app.Activity;
import android.media.tv.TvView;
import android.media.tv.interactive.TvInteractiveAppView;
import android.os.Bundle;
import android.tv.cts.R;

public class TvInteractiveAppViewStubActivity extends Activity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.tviappview_layout);
    }

    public TvInteractiveAppView getTvInteractiveAppView() {
        return findViewById(R.id.tviappview);
    }

    public TvView getTvView() {
        return findViewById(R.id.tviapp_tvview);
    }
}
