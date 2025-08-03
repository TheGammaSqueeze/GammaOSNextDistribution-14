/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.uirendering.cts.runner;

import android.app.Instrumentation;
import android.os.Bundle;
import android.util.Log;

import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.runner.Description;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import java.io.File;
import java.util.ArrayList;
import java.util.function.Function;

public class TestArtifactCollector extends RunListener {

    private static TestArtifactCollector sInstance = null;
    private static final String TAG = "UiRendering";

    private class TestArtifact {
        private final String mKey = "uirendering_" + System.nanoTime();

        private final Function<File, Void> mPopulateFile;
        private final String mFileName;
        private File mFile;

        TestArtifact(String fileName, Function<File, Void> populateFile) {
            mFileName = fileName;
            mPopulateFile = populateFile;
        }

        void addToBundle(Bundle bundle, Description activeTest) {
            if (mFile == null) {
                populateFile(activeTest);
            }
            bundle.putString(mKey, bypassContentProvider(mFile));
        }

        private void populateFile(Description activeTest) {
            String verboseName = activeTest.getTestClass().getSimpleName() + "_"
                    + activeTest.getMethodName() + "_" + mFileName;
            // Filter out any weird characters just in case
            verboseName = verboseName.replaceAll("[^a-zA-Z0-9._\\[\\]]+", "_");
            mFile = new File(mDumpDirectory, verboseName);
            mPopulateFile.apply(mFile);
        }
    }

    // Magic number for an in-progress status report
    private static final int INST_STATUS_IN_PROGRESS = 2;
    private File mDumpDirectory;
    private Instrumentation mInstrumentation;
    private ArrayList<TestArtifact> mTestArtifacts = new ArrayList<>();
    private ArrayList<Function<Failure, Void>> mPreFailureCallbacks = new ArrayList<>();

    public TestArtifactCollector() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mDumpDirectory = mInstrumentation.getContext().getExternalCacheDir();
        if (mDumpDirectory == null) {
            mDumpDirectory = mInstrumentation.getContext().getCacheDir();
        }
        mDumpDirectory.mkdirs();

        // Cleanup old tests
        // These are removed on uninstall anyway but just in case...
        File[] toRemove = mDumpDirectory.listFiles();
        if (toRemove != null && toRemove.length > 0) {
            for (File file : toRemove) {
                deleteContentsAndDir(file);
            }
        }
        synchronized (TestArtifactCollector.class) {
            if (sInstance == null) {
                sInstance = this;
            } else {
                throw new RuntimeException("TestArtifactCollector double-created?");
            }
        }
    }

    private static boolean deleteContentsAndDir(File dir) {
        if (deleteContents(dir)) {
            return dir.delete();
        } else {
            return false;
        }
    }

    private static boolean deleteContents(File dir) {
        File[] files = dir.listFiles();
        boolean success = true;
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    success &= deleteContents(file);
                }
                if (!file.delete()) {
                    Log.w(TAG, "Failed to delete " + file);
                    success = false;
                }
            }
        }
        return success;
    }

    private static String bypassContentProvider(File file) {
        // TradeFed currently insists on bouncing off of a content provider for the path
        // we are using, but that content provider will never have permissions
        // Since we want to avoid needing to use requestLegacyStorage & there's currently no
        // option to tell TF to not use the content provider, just break its file
        // detection pattern
        // b/183140644
        return "/." + file.getAbsolutePath();
    }

    @Override
    public void testStarted(Description description) throws Exception {
        // Just in case
        mTestArtifacts.clear();
    }

    @Override
    public void testFailure(Failure failure) throws Exception {
        for (Function<Failure, Void> listener : mPreFailureCallbacks) {
            listener.apply(failure);
        }

        if (mTestArtifacts.size() > 0) {
            Description failingTest = failure.getDescription();
            Bundle report = new Bundle();
            for (TestArtifact artifact : mTestArtifacts) {
                artifact.addToBundle(report, failingTest);
            }
            mInstrumentation.sendStatus(INST_STATUS_IN_PROGRESS, report);
        }
    }

    @Override
    public void testFinished(Description description) throws Exception {
        mTestArtifacts.clear();
    }

    /**
     * Add a bitmap that will be dumped on test failure
     */
    public static void addArtifact(String fileName, Function<File, Void> populateFile) {
        sInstance.doAddArtifact(fileName, populateFile);
    }

    private void doAddArtifact(String fileName, Function<File, Void> populateFile) {
        mTestArtifacts.add(new TestArtifact(fileName, populateFile));
    }

    /**
     * Add a callback for when a test failure happens but log files have not yet been sent
     * to the host. This allows on-demand gathering of heavy-weight artifacts (ie, full device
     * screenshots)
     */
    public static void addFailureListener(Function<Failure, Void> listener) {
        sInstance.mPreFailureCallbacks.add(listener);
    }
}
