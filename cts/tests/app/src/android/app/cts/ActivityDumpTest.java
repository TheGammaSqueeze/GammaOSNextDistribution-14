/*
 * Copyright (C) 2022 The Android Open Source Project
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
package android.app.cts;

import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.content.Context;
import android.util.Dumpable;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Arrays;

public final class ActivityDumpTest {

    private static final String TAG = ActivityDumpTest.class.getSimpleName();

    private static final String DEFAULT_NAME = "DUMPABLE";
    private static final String DEFAULT_CONTENT = "The name is Able, Dump Able!";

    private CustomActivity mActivity;

    @Before
    @UiThreadTest // Needed to create activity
    public void setActivity() throws Exception {
        mActivity = new CustomActivity(ApplicationProvider.getApplicationContext());
        Log.i(TAG, "setActivity: activity=" + mActivity
                + ", targetSdk=" + mActivity.getApplicationInfo().targetSdkVersion);
    }

    @Test
    public void testAddDumpable_oneOnly() throws Exception {
        mActivity.addDumpable(new CustomDumpable(DEFAULT_NAME, DEFAULT_CONTENT));

        String dump = dump(mActivity);

        assertWithMessage("dump() (expected to have name)").that(dump).contains(DEFAULT_NAME);
        assertWithMessage("dump() (expected to have content)").that(dump).contains(DEFAULT_CONTENT);
    }

    @Test
    public void testAddDumpable_twoWithDistinctNames() throws Exception {
        mActivity.addDumpable(new CustomDumpable("dump1", "able1"));
        mActivity.addDumpable(new CustomDumpable("dump2", "able2"));

        String dump = dump(mActivity);

        assertWithMessage("dump() (expected to have name1)").that(dump).contains("dump1");
        assertWithMessage("dump() (expected to have content1)").that(dump).contains("able1");
        assertWithMessage("dump() (expected to have name2)").that(dump).contains("dump2");
        assertWithMessage("dump() (expected to have content2)").that(dump).contains("able2");
    }

    @Test
    public void testAddDumpable_twoWithSameName() throws Exception {
        mActivity.addDumpable(new CustomDumpable("dump", "able1"));
        mActivity.addDumpable(new CustomDumpable("dump", "able2"));

        String dump = dump(mActivity);

        assertWithMessage("dump() (expected to have name)").that(dump).contains("dump");
        assertWithMessage("dump() (expected to have content1)").that(dump).contains("able1");
        assertWithMessage("dump() (expected to NOT have content2)").that(dump)
                .doesNotContain("able2");
    }

    @Test
    public void testDump_autofill() throws Exception {
        legacyArgDumpTest("--autofill", "AutofillManager");
    }

    @Test
    public void testDump_contentCapture() throws Exception {
        legacyArgDumpTest("--contentcapture", "ContentCaptureManager");
    }

    @Test
    public void testDump_translation() throws Exception {
        legacyArgDumpTest("--translation", "UiTranslationController");
    }

    private void legacyArgDumpTest(String arg, String dumpableName) throws IOException {
        String baselineDump = dump(mActivity);

        String legacyArgDump = dump(mActivity, arg);
        String equivalentDumpableDump = dump(mActivity, "--dump-dumpable", dumpableName);

        assertWithMessage("dump([%s])", arg).that(legacyArgDump).isNotEqualTo(baselineDump);
        assertWithMessage("dump([%s])", arg).that(legacyArgDump)
                .isNotEqualTo(equivalentDumpableDump);
        assertWithMessage("dump([%s])", arg).that(legacyArgDump).contains(arg);
        assertWithMessage("dump([%s])", arg).that(legacyArgDump).contains("deprecated");
        assertWithMessage("dump([%s])", arg).that(legacyArgDump)
                .contains("--dump-dumpable " + dumpableName);
    }

    @Test
    public void testDump_listDumpables() throws Exception {
        String baselineDump = dump(mActivity);

        mActivity.addDumpable(new CustomDumpable(DEFAULT_NAME, DEFAULT_CONTENT));

        String listDumpables = dump(mActivity, "--list-dumpables");

        assertWithMessage("dump(--list-dumpables)").that(listDumpables).contains(DEFAULT_NAME);
        assertWithMessage("dump(--list-dumpables)").that(listDumpables)
                .doesNotContain(DEFAULT_CONTENT);
        assertWithMessage("dump(--list-dumpables)").that(listDumpables)
                .doesNotContain(baselineDump);
    }

    @Test
    public void testDump_dumpDumpable() throws Exception {
        String prefix = "123";
        mActivity.addDumpable(new CustomDumpable(DEFAULT_NAME, DEFAULT_CONTENT));

        String dumpDumpable = dumpInternal(mActivity, prefix, "--dump-dumpable", DEFAULT_NAME);

        assertWithMessage("dump(--dump-dumpable %s)", DEFAULT_NAME).that(dumpDumpable)
                .isEqualTo(String.format("%s%s:\n%s%s%s\n",
                        prefix, DEFAULT_NAME, // line 1
                        prefix, prefix, DEFAULT_CONTENT)); // line 2
    }

    private String dump(Activity activity) throws IOException {
        return dumpInternal(activity, /* prefix= */ "", /* args= */ (String[]) null);
    }

    private String dump(Activity activity, String... args) throws IOException {
        return dumpInternal(activity, /* prefix= */ "", args);
    }

    private String dumpInternal(Activity activity, String prefix, @Nullable String... args)
            throws IOException {
        String argsString = "";
        if (args != null) {
            argsString = " with args " + Arrays.toString(args);
        }
        Log.d(TAG, "dumping " + activity + argsString);
        String dump;
        try (StringWriter sw = new StringWriter(); PrintWriter writer = new PrintWriter(sw)) {
            // Must call dumpInternal() (instad of dump()) so special args are handled
            activity.dumpInternal(prefix, /* fd= */ null, writer, args);
            dump = sw.toString();
        }
        assertWithMessage("dump(%s)", argsString).that(dump).isNotEmpty();
        Log.v(TAG, "result (" + dump.length() + " chars):\n" + dump);
        return dump;
    }

    // Needs a custom class to call attachBaseContext(), otherwise dump() would fail because
    // getResources() and other methods (like getSystemService(...) would return null.
    private static final class CustomActivity extends Activity {

        CustomActivity(Context context) {
            attachBaseContext(context);
        }
    }

    private static final class CustomDumpable implements Dumpable {
        public final String name;
        public final String content;

        private CustomDumpable(String name, String content) {
            this.name = name;
            this.content = content;
        }

        @Override
        public String getDumpableName() {
            return name;
        }

        @Override
        public void dump(PrintWriter writer, String[] args) {
            writer.println(content);
        }
    }
}
