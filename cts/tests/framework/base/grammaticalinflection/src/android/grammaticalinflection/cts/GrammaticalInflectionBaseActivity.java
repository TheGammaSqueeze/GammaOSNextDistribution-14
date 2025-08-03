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

package android.grammaticalinflection.cts;

import android.content.ContentProviderClient;
import android.net.Uri;
import android.server.wm.CommandSession;
import android.server.wm.TestJournalProvider;
import android.server.wm.TestJournalProvider.TestJournalClient;

public class GrammaticalInflectionBaseActivity  extends CommandSession.BasicTestActivity {
    private static final Uri URI = Uri.parse(
            "content://android.server.grammaticalinflection.testjournalprovider");

    @Override
    protected TestJournalClient createTestJournalClient() {
        // Create our own provider to avoid name conflict at installation.
        final ContentProviderClient client = this.getContentResolver()
                .acquireContentProviderClient(URI);
        return new TestJournalProvider.TestJournalClient(client,
                getComponentName().flattenToShortString());
    }
}
