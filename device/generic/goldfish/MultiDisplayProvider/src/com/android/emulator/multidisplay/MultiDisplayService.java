/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.emulator.multidisplay;

import android.app.Service;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Messenger;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.util.DebugUtils;
import android.util.Log;
import android.view.Surface;

import java.lang.Thread;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public final class MultiDisplayService extends Service {
    private static final String TAG = "MultiDisplayService";
    private static final String DISPLAY_NAME = "Emulator 2D Display";
    private static final String[] UNIQUE_DISPLAY_ID = new String[]{"notUsed", "1234562",
                                                                   "1234563", "1234564",
                                                                   "1234565", "1234566",
                                                                   "1234567", "1234568",
                                                                   "1234569", "1234570",
                                                                   "1234571"};
    private static final int MAX_DISPLAYS = 10;
    private static final int ADD = 1;
    private static final int DEL = 2;
    // the following is used by resizabel to set display
    // intentionally shifted 4 bits to avoid conflicting
    // with existing multidisplay functions
    private static final int SET_DISPLAY = 0x10;

    private static final int FLAGS = DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC |
                                      DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                                      DisplayManager.VIRTUAL_DISPLAY_FLAG_ROTATES_WITH_CONTENT |
                                      DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED |
                                      1 << 6 |//DisplayManager.VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH
                                      1 << 9; //DisplayManager.VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;

    private static final String SURFACE_COMPOSER_INTERFACE_KEY = "android.ui.ISurfaceComposer";
    private IBinder mSurfaceFlinger;
    private DisplayManager mDisplayManager;
    private VirtualDisplay mVirtualDisplay[];
    private Surface mSurface[];
    private Messenger mMessenger;
    private ListenerThread mListener;

    private final Handler mHandler = new Handler();

    final class MultiDisplay {
        public int width;
        public int height;
        public int dpi;
        public int flag;
        public VirtualDisplay virtualDisplay;
        public Surface surface;
        public boolean enabled;

        MultiDisplay() {
            clear();
        }

        public void clear() {
            width = 0;
            height = 0;
            dpi = 0;
            flag = 0;
            virtualDisplay = null;
            surface = null;
            enabled = false;
        }

        public void set(int w, int h, int d, int f) {
            width = w;
            height = h;
            dpi = d;
            flag = f;
            enabled = true;
        }

        public boolean match(int w, int h, int d, int f) {
            return (w == width && h == height && d == dpi && f == flag);
        }

        private void dump(PrintWriter writer, String prefix, boolean printHeader) {
            if (!enabled) {
                if (printHeader) {
                    writer.println("disabled");
                }
                return;
            }
            if (printHeader) {
                writer.println("enabled");
            }
            writer.printf("%sDimensions: %dx%d\n", prefix, width, height);
            writer.printf("%sDPI: %d\n", prefix, dpi);
            writer.printf("%sflags: %s\n", prefix, flagsToString(flag));
            writer.printf("%svirtualDisplay: %s\n", prefix, virtualDisplay);
            writer.printf("%ssurface: %s\n", prefix, surface);
        }

        @Override
        public String toString() {
            return "MultiDisplay[dimensions=" + width + "x" + height
                    + ", dpi=" + dpi
                    + ", enabled=" + enabled
                    + ", flags=" + flagsToString(flag)
                    + ", virtualDisplay=" + virtualDisplay
                    + ", surface=" + surface
                    + "]";
        }
    }
    private MultiDisplay mMultiDisplay[];

    @Override
    public void onCreate() {
        Log.i(TAG, "Creating service");

        super.onCreate();

        try {
            System.loadLibrary("emulator_multidisplay_jni");
        } catch (Exception e) {
            Log.e(TAG, "Failed to loadLibrary: " + e);
        }

        mListener = new ListenerThread();
        mListener.start();

        mDisplayManager = getSystemService(DisplayManager.class);
        mMultiDisplay = new MultiDisplay[MAX_DISPLAYS + 1];
        for (int i = 0; i < MAX_DISPLAYS + 1; i++) {
            Log.d(TAG, "Creating display " + i);
            mMultiDisplay[i] = new MultiDisplay();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if(mMessenger == null)
            mMessenger = new Messenger(mHandler);
        return mMessenger.getBinder();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // keep it alive.
        return START_STICKY;
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (args == null || args.length == 0) {
            dump(writer);
        } else {
            runCmd(writer, args);
        }
    };

    private void dump(PrintWriter writer) {
       writer.printf("Max displays: %d\n", MAX_DISPLAYS);
       writer.printf("Unique display ids: %s\n", Arrays.toString(UNIQUE_DISPLAY_ID));
       writer.printf("Default flags: %s\n", flagsToString(FLAGS));
       dumpArray(writer, mVirtualDisplay, "virtual display");
       dumpArray(writer, mSurface, "surface");

       if (mMultiDisplay != null) {
           int size = mMultiDisplay.length;
           writer.printf("# of multi displays: %d\n", size);
           for (int i = 0; i < size; i++) {
               writer.printf("  MultiDisplay #%d: ", i);
               mMultiDisplay[i].dump(writer, "    ", /* printHeader= */ true);
           }
       } else {
           writer.println("No multi display");
       }
    }

    private void dumpArray(PrintWriter writer, Object[] array, String name) {
        if (array != null) {
            int size = array.length;
            writer.printf("# of %ss: %d\n", name, size);
            for (int i = 0; i < size; i++) {
                writer.printf("  %d: %s\n", i, array[i]);
            }
        } else {
            writer.printf("No %s\n", name);
        }
    }

    private void runCmd(PrintWriter writer, String[] args) {
        String cmd = args[0];

        switch (cmd) {
            case "add":
                runCmdAdd(writer, args);
                break;
            case "del":
                runCmdDel(writer, args);
                break;
            case "list":
                runCmdList(writer);
                break;
            default:
                writer.printf("Invalid command: %s. Valid options are: \n", cmd);
            case "help":
                runCmdHelp(writer);
        }
    }

    private void runCmdHelp(PrintWriter writer) {
        writer.println("  help - shows this help");
        writer.println("  list - list all virtual displays created by this tool");
        writer.println("  add <display_id> <width> <height> <dpi> <flags> - add a new virtual "
                + "display with the given properties");
        writer.println("  del <display_id> - delete the given virtual display");
    }

    private void runCmdList(PrintWriter writer) {
        if (mMultiDisplay == null) {
            writer.println("No multi display");
            return;
        }

        List<MultiDisplay> enabledDisplays = Arrays.stream(mMultiDisplay).filter(d -> d.enabled)
                .collect(Collectors.toList());

        if (enabledDisplays.isEmpty()) {
            writer.println("No multi display added by the tool");
            return;
        }

        int size = enabledDisplays.size();
        writer.printf("%d display%s\n", size, (size == 1? "" : "s"));
        for (int i = 0; i < size; i++) {
            writer.printf("Display %d:\n", i);
            enabledDisplays.get(i).dump(writer, "  ", /* printHeader= */ false);
        }
    }

    private void runCmdAdd(PrintWriter writer, String[] args) {
        if (!hasExactlyArgs(writer, args, 6)) return;

        int displayId = getIntArg(writer, args, 1);
        int width = getIntArg(writer, args, 2);
        int height = getIntArg(writer, args, 3);
        int dpi = getIntArg(writer, args, 4);
        int flags = getIntArg(writer, args, 5);

        addVirtualDisplay(displayId, width, height, dpi, flags);

        writer.printf("Display %d added \n", displayId);
    }

    private void runCmdDel(PrintWriter writer, String[] args) {
        if (!hasExactlyArgs(writer, args, 2)) return;

        int displayId = getIntArg(writer, args, 1);

        deleteVirtualDisplay(displayId);

        writer.printf("Display %d deleted\n", displayId);
    }

    private boolean hasExactlyArgs(PrintWriter writer, String[] args, int expectedSize) {
        if (args.length != expectedSize) {
            writer.printf("invalid number of arguments (%d) for command %s (expected %d).\n"
                    + "Valid command:\n",
                    args.length, args[0], expectedSize);
            runCmdHelp(writer);
            return false;
        }
        return true;
    }

    private int getIntArg(PrintWriter writer, String[] args, int index) {
        String value = "TBD";
        try {
            value = args[index];
            return Integer.parseInt(value);
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid integer at index " + index + ": " + value);
        }
    }

    private void deleteVirtualDisplay(int displayId) {
        Log.d(TAG, "deleteVirtualDisplay(" + displayId + ")");
        if (!mMultiDisplay[displayId].enabled) {
            return;
        }
        if (mMultiDisplay[displayId].virtualDisplay != null) {
            mMultiDisplay[displayId].virtualDisplay.release();
        }
        if (mMultiDisplay[displayId].surface != null) {
            mMultiDisplay[displayId].surface.release();
        }
        mMultiDisplay[displayId].clear();
        nativeReleaseListener(displayId);
    }

    private void createVirtualDisplay(int displayId, int w, int h, int dpi, int flag) {
        mMultiDisplay[displayId].surface = nativeCreateSurface(displayId, w, h);
        mMultiDisplay[displayId].virtualDisplay = mDisplayManager.createVirtualDisplay(
                                          null /* projection */,
                                          DISPLAY_NAME, w, h, dpi,
                                          mMultiDisplay[displayId].surface, flag,
                                          null /* callback */,
                                          null /* handler */,
                                          UNIQUE_DISPLAY_ID[displayId]);
        mMultiDisplay[displayId].set(w, h, dpi, flag);
    }

    private void addVirtualDisplay(int displayId, int w, int h, int dpi, int flag) {
        Log.d(TAG, "addVirtualDisplay(id=" + displayId + ", w=" + w + ", h=" + h
                + ", dpi=" + dpi + ", flags=" + flagsToString(flag) + ")");
        if (mMultiDisplay[displayId].match(w, h, dpi, flag)) {
            return;
        }
        if (mMultiDisplay[displayId].virtualDisplay == null) {
            createVirtualDisplay(displayId, w, h, dpi, flag);
            return;
        }
        if (mMultiDisplay[displayId].flag != flag) {
            deleteVirtualDisplay(displayId);
            createVirtualDisplay(displayId, w, h, dpi, flag);
            return;
        }
        if (mMultiDisplay[displayId].width != w || mMultiDisplay[displayId].height != h) {
            nativeResizeListener(displayId, w, h);
        }
        // only dpi changes
        mMultiDisplay[displayId].virtualDisplay.resize(w, h, dpi);
        mMultiDisplay[displayId].set(w, h, dpi, flag);
    }

    class ListenerThread extends Thread {
        ListenerThread() {
            super(TAG);
        }

        @Override
        public void run() {
            while(nativeOpen() <= 0) {
                Log.e(TAG, "failed to open multiDisplay pipe, retry");
            }
            Log.d(TAG, "success open multiDisplay pipe");
            while(true) {
                Log.d(TAG, "waiting to read pipe");
                int[] array = {0, 0, 0, 0, 0, 0};
                if (!nativeReadPipe(array)) {
                    Log.e(TAG, "failed and try again");
                    continue;
                }
                Log.d(TAG, "have read something from pipe");
                Log.d(TAG, "run(): array= " + Arrays.toString(array));
                switch (array[0]) {
                    case ADD: {
                        for (int j = 0; j < 6; j++) {
                            Log.d(TAG, "received " + array[j]);
                        }
                        int i = array[1];
                        int width = array[2];
                        int height = array[3];
                        int dpi = array[4];
                        int flag = (array[5] != 0) ? array[5] : FLAGS;
                        if (i < 1 || i > MAX_DISPLAYS || width <=0 || height <=0 || dpi <=0
                            || flag < 0) {
                            Log.e(TAG, "invalid parameters for add/modify display");
                            break;
                        }
                        addVirtualDisplay(i, width, height, dpi, flag);
                        break;
                    }
                    case DEL: {
                        int i = array[1];
                        if (i < 1 || i > MAX_DISPLAYS) {
                            Log.e(TAG, "invalid parameters for delete display");
                            break;
                        }
                        deleteVirtualDisplay(i);
                        break;
                    }
                    case SET_DISPLAY: {
                         for (int j = 0; j < 6; j++) {
                             Log.d(TAG, "SET_DISPLAY received " + array[j]);
                         }
                         if (mSurfaceFlinger == null) {
                             Log.d(TAG, "obtain surfaceflinger " );
                             mSurfaceFlinger = ServiceManager.getService("SurfaceFlinger");
                         }
                         if (mSurfaceFlinger != null) {
                             int i = array[1];
                             Parcel data = Parcel.obtain();
                             data.writeInterfaceToken(SURFACE_COMPOSER_INTERFACE_KEY);
                             data.writeInt(i);
                             try {
                                 if (i >=0) {
                                    mSurfaceFlinger.transact(1035, data, null, 0 /* flags */);
                                    Log.d(TAG, "setting display to " + i);
                                 } else {
                                    Log.e(TAG, "invalid display id " + i);
                                 }
                             } catch (RemoteException e) {
                                 Log.e(TAG, "Could not set display:" + e.toString());
                             }
                         } else {
                             Log.e(TAG, "cannot get SurfaceFlinger service");
                         }
                        break;
                    }
                    // TODO(b/231763427): implement LIST
                }
            }
        }
    }

    private static String flagsToString(int flags) {
        return DebugUtils.flagsToString(DisplayManager.class, "VIRTUAL_DISPLAY_FLAG_", flags);
    }

    private native int nativeOpen();
    private native Surface nativeCreateSurface(int displayId, int width, int height);
    private native boolean nativeReadPipe(int[] arr);
    private native boolean nativeReleaseListener(int displayId);
    private native boolean nativeResizeListener(int displayId, int with, int height);
}
