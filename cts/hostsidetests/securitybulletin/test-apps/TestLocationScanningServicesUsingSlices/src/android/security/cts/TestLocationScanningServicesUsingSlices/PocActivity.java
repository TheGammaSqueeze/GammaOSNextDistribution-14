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

package android.security.cts.TestLocationScanningServicesUsingSlices;

import static android.app.slice.SliceItem.FORMAT_ACTION;
import static android.app.slice.SliceItem.FORMAT_SLICE;

import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.slice.Slice;
import androidx.slice.SliceItem;
import androidx.slice.widget.SliceLiveData;

public class PocActivity extends FragmentActivity {

    @Override
    protected void onResume() {
        try {
            super.onResume();
            LiveData sliceLiveData =
                    SliceLiveData.fromUri(this /* Context */, getIntent().getData());
            sliceLiveData.observe(
                    this /* LifecycleOwner */,
                    slice -> {
                        if (slice != null) {
                            for (SliceItem outerItem : ((Slice) slice).getItems()) {
                                if (outerItem.getFormat().equals(FORMAT_SLICE)) {
                                    for (SliceItem innerItem : outerItem.getSlice().getItems()) {
                                        if (innerItem.getFormat().equals(FORMAT_ACTION)) {
                                            try {
                                                innerItem.fireAction(null, null);
                                            } catch (Exception ignored) {
                                                // Ignore all exceptions. Any exception here results
                                                // in assumption failure in devicetest and prints
                                                // appropriate logs
                                            }
                                        }
                                    }
                                    break;
                                }
                            }
                        }
                    });
        } catch (Exception ignored) {
            // Ignore all exceptions. Any exception here results in assumption failure in devicetest
            // and prints appropriate logs
        }
    }
}
