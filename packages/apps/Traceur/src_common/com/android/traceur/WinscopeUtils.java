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
 * limitations under the License
 */

package com.android.traceur;

import android.content.ContentResolver;
import android.os.FileUtils;
import android.os.RemoteException;
import android.provider.Settings;
import android.system.Os;
import android.util.Log;
import android.view.WindowManagerGlobal;

import com.android.internal.inputmethod.ImeTracing;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Utility functions for managing Winscope traces that have not been migrated to perfetto yet.
 */
public class WinscopeUtils {
    private static final String TAG = "Traceur";
    private static final String SETTINGS_VIEW_CAPTURE_ENABLED = "view_capture_enabled";
    private static final String VIEW_CAPTURE_FILE_SUFFIX = ".vc";
    private static final String WM_TRACE_DIR = "/data/misc/wmtrace/";
    private static final File[] CONSISTENTLY_NAMED_TRACE_FILES = {
            new File(WM_TRACE_DIR + "wm_trace.winscope"),
            new File(WM_TRACE_DIR + "wm_log.winscope"),
            new File(WM_TRACE_DIR + "ime_trace_clients.winscope"),
            new File(WM_TRACE_DIR + "ime_trace_managerservice.winscope"),
            new File(WM_TRACE_DIR + "ime_trace_service.winscope"),
    };

    public static void traceStart(ContentResolver contentResolver,
            boolean isWinscopeTracingEnabled) {
        // Ensure all tracing sessions are initially stopped (buffers don't contain old data)
        traceStop(contentResolver);

        // Ensure there are no stale files on disk that could be
        // picked up later by WinscopeUtils#traceDump()
        deleteTempTraceFiles();

        if (isWinscopeTracingEnabled) {
            setWindowTraceEnabled(true);
            setImeTraceEnabled(true);
            setViewCaptureEnabled(contentResolver, true);
        }
    }

    public static void traceStop(ContentResolver contentResolver) {
        if (isWindowTraceEnabled()) {
            setWindowTraceEnabled(false);
        }
        if (isImeTraceEnabled()) {
            setImeTraceEnabled(false);
        }
        if (isViewCaptureEnabled(contentResolver)) {
            setViewCaptureEnabled(contentResolver, false);
        }
    }

    /**
     * This method is responsible for getting all trace files that will be useful for debugging so
     * they can be inspected using go/winscope. In the /data/misc/wmtrace directory where these
     * traces are stored, .vc (ViewCapture) files are named on a per-app basis, which means we have
     * to collect X amount of them. In contrast, the winscope files are named the same everytime,
     * and we simply have to check a predetermined set of file paths.
     *
     * @return File handles to all the useful trace files in the /data/misc/wmtrace directory.
     */
    private static List<File> getTraceFilesFromWmTraceDir() {
        List<File> traceFiles = new ArrayList<>();
        File[] wmTraceFiles = new File(WM_TRACE_DIR).listFiles();
        if (wmTraceFiles != null) {
            for (File possibleViewCaptureFile : wmTraceFiles) {
                if (possibleViewCaptureFile.isFile()
                        && possibleViewCaptureFile.getName().endsWith(VIEW_CAPTURE_FILE_SUFFIX)) {
                    traceFiles.add(possibleViewCaptureFile);
                }
            }
        }
        for (File possibleWinscopeTraceFile : CONSISTENTLY_NAMED_TRACE_FILES) {
            if (possibleWinscopeTraceFile.exists()) {
                traceFiles.add(possibleWinscopeTraceFile);
            }
        }

        return traceFiles;
    }

    public static List<File> traceDump(ContentResolver contentResolver, String perfettoFilename) {
        traceStop(contentResolver);

        ArrayList<File> files = new ArrayList();

        for (File tempFile : getTraceFilesFromWmTraceDir()) {
            try {
                // Make a copy of the winscope traces to change SELinux file context from
                // "wm_trace_data_file" to "trace_data_file", otherwise other apps might not be able
                // to read the trace files shared by Traceur.
                File outFile = getOutputFile(perfettoFilename, tempFile);
                FileUtils.copy(tempFile, outFile);
                outFile.setReadable(true, false); // (readable, ownerOnly)
                outFile.setWritable(true, false); // (writable, ownerOnly)
                files.add(outFile);
                Log.v(TAG, "Copied winscope trace file " + outFile.getCanonicalPath());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        deleteTempTraceFiles();

        return files;
    }

    private static boolean isWindowTraceEnabled() {
        try {
            return WindowManagerGlobal.getWindowManagerService().isWindowTraceEnabled();
        } catch (RemoteException e) {
            Log.e(TAG,
                    "Could not get window trace status, defaulting to false." + e);
        }
        return false;
    }

    private static void setWindowTraceEnabled(boolean toEnable) {
        try {
            if (toEnable) {
                WindowManagerGlobal.getWindowManagerService().startWindowTrace();
                Log.v(TAG, "Started window manager tracing");
            } else {
                WindowManagerGlobal.getWindowManagerService().stopWindowTrace();
                Log.v(TAG, "Stopped window manager tracing");
            }
        } catch (RemoteException e) {
            Log.e(TAG, "Could not set window trace status." + e);
        }
    }

    private static boolean isImeTraceEnabled() {
        return ImeTracing.getInstance().isEnabled();
    }

    private static void setImeTraceEnabled(boolean toEnable) {
        if (toEnable) {
            ImeTracing.getInstance().startImeTrace();
            Log.v(TAG, "Started IME tracing");
        } else {
            ImeTracing.getInstance().stopImeTrace();
            Log.v(TAG, "Stopped IME tracing");
        }
    }

    private static boolean isViewCaptureEnabled(ContentResolver contentResolver) {
        return Settings.Global.getInt(contentResolver, SETTINGS_VIEW_CAPTURE_ENABLED, 0) != 0;
    }

    private static void setViewCaptureEnabled(ContentResolver contentResolver, boolean toEnable) {
        if (toEnable) {
            Settings.Global.putInt(contentResolver, SETTINGS_VIEW_CAPTURE_ENABLED, 1);
            Log.v(TAG, "Started view capture tracing");
        } else {
            Settings.Global.putInt(contentResolver, SETTINGS_VIEW_CAPTURE_ENABLED, 0);
            Log.v(TAG, "Stopped view capture tracing");
        }
    }

    // Create an output file that combines Perfetto and Winscope trace file names. E.g.:
    // Perfetto trace filename: "trace-oriole-MAIN-2023-08-16-17-30-38.perfetto-trace"
    // Winscope trace filename: "wm_trace.winscope"
    // Output filename: "trace-oriole-MAIN-2023-08-16-17-30-38-wm_trace.winscope"
    private static File getOutputFile(String perfettoFilename, File tempFile) {
        String perfettoFilenameWithoutExtension =
            perfettoFilename.substring(0, perfettoFilename.lastIndexOf('.'));
        String filename = perfettoFilenameWithoutExtension + "-" + tempFile.getName();
        return TraceUtils.getOutputFile(filename);
    }

    private static void deleteTempTraceFiles() {
        for (File file : getTraceFilesFromWmTraceDir()) {
            if (!file.exists()) {
                continue;
            }

            try {
                Os.unlink(file.getAbsolutePath());
                Log.v(TAG, "Deleted winscope trace file " + file);
            } catch (Exception e){
                Log.e(TAG, "Failed to delete winscope trace file " + file, e);
            }
        }
    }
}
