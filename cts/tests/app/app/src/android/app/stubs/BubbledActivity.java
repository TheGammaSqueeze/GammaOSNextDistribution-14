/*
 * Copyright (C) 2019 The Android Open Source Project
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

package android.app.stubs;

import android.app.Activity;
import android.content.LocusId;
import android.os.Bundle;

/**
 * Used by NotificationManagerBubbleTest for testing policy around bubbles, this activity is shown
 * within the bubble (and sometimes outside too depending on the test).
 */
public class BubbledActivity extends Activity {

    public static final String EXTRA_LOCUS_ID = "EXTRA_ID_LOCUS_ID";
    private LocusId mLocusId;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        Bundle b = getIntent().getExtras();
        String locus = b != null ? b.getString(EXTRA_LOCUS_ID, null) : null;
        mLocusId = locus != null ? new LocusId(locus) : null;
        setLocusContext(mLocusId, null /* bundle */);
    }

    public LocusId getLocusId() {
        return mLocusId;
    }
}
