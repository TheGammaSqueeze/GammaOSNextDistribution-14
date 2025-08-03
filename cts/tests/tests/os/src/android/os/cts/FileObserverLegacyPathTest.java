/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.os.cts;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.ConditionVariable;
import android.os.FileObserver;
import android.platform.test.annotations.AppModeFull;
import android.provider.MediaStore;
import android.test.AndroidTestCase;
import java.io.File;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class FileObserverLegacyPathTest extends AndroidTestCase {
    ConditionVariable mCond;
    Context mContext;
    File mTestDir;

    @Override
    protected void setUp() throws Exception {
        mContext = getContext();
        mCond = new ConditionVariable();

        mTestDir = new File("/sdcard/DCIM/testdir");
        mTestDir.delete();
        mTestDir.mkdirs();
    }

    @Override
    protected void tearDown() throws Exception {
        mTestDir.delete();
    }

    /* This test creates a jpg image file and write some test data to that
     * file.
     * It verifies that FileObserver is able to catch the CREATE, OPEN and
     * MODIFY events on that file, ensuring that, in the case of a FUSE mounted
     * file system, changes applied to the lower file system will be detected
     * by a monitored FUSE folder.
     * Instead of checking if the set of generated events is exactly the same
     * as the set of expected events, the test checks if the set of generated
     * events contains CREATE, OPEN and MODIFY. This because there may be other
     * services (e.g., file indexing) that may access the newly created file,
     * generating spurious events that this test doesn't care of and filters
     * them out. */
    @AppModeFull(reason = "Instant apps cannot access external storage")
    public void testCreateFile() throws Exception {
        String imageName = "image" + System.currentTimeMillis() + ".jpg";

        final Integer eventsMask = FileObserver.OPEN | FileObserver.CREATE | FileObserver.MODIFY;
        PathFileObserver fileObserver =
                new PathFileObserver(mTestDir, eventsMask, mCond, Map.of(imageName, eventsMask));
        fileObserver.startWatching();

        ContentValues cv = new ContentValues();
        cv.put(MediaStore.Files.FileColumns.DISPLAY_NAME, imageName);
        cv.put(MediaStore.Files.FileColumns.RELATIVE_PATH, "DCIM/testdir");
        cv.put(MediaStore.Files.FileColumns.MIME_TYPE, "image/jpg");

        Uri imageUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL);

        Uri fileUri = mContext.getContentResolver().insert(imageUri, cv);

        OutputStream os = mContext.getContentResolver().openOutputStream(fileUri);
        os.write("TEST".getBytes("UTF-8"));
        os.close();

        /* Wait for for the inotify events to be caught. A timeout occurs after
         * 2 seconds. */
        mCond.block(2000);

        int detectedEvents = fileObserver.getEvents().getOrDefault(imageName, 0);

        /* Verify if the received events correspond to the ones that were requested */
        assertEquals("Expected and received inotify events do not match",
            PathFileObserver.eventsToSet(eventsMask),
            PathFileObserver.eventsToSet(detectedEvents & eventsMask));

        fileObserver.stopWatching();
    }

    static public class PathFileObserver extends FileObserver {
        Map<String, Integer> mGeneratedEventsMap;
        Map<String, Integer> mMonitoredEventsMap;
        final ConditionVariable mCond;
        final int mEventsMask;

        public PathFileObserver(final File root, final int mask, ConditionVariable condition,
                Map<String, Integer> monitoredFiles) {
            super(root, FileObserver.ALL_EVENTS);

            mEventsMask = mask;
            mCond = condition;
            mGeneratedEventsMap = new HashMap<>();
            mMonitoredEventsMap = monitoredFiles;
        }

        public Map<String, Integer> getEvents() { return mGeneratedEventsMap; }

        public void onEvent(final int event, final String path) {
            /* There might be some extra flags introduced by inotify.h.  Remove
             * them. */
            final int filteredEvent = event & FileObserver.ALL_EVENTS;
            if (filteredEvent == 0)
                return;

            /* Update the event bitmap of the associated file. */
            mGeneratedEventsMap.put(
                    path, filteredEvent | mGeneratedEventsMap.getOrDefault(path, 0));

            /* Release the condition variable only if at least all the matching
             * events have been caught for every monitored file. */
            for (String file : mMonitoredEventsMap.keySet()) {
                int monitoredEvents = mMonitoredEventsMap.getOrDefault(file, 0);
                int generatedEvents = mGeneratedEventsMap.getOrDefault(file, 0);

                if ((generatedEvents & monitoredEvents) != monitoredEvents)
                    return;
            }

            mCond.open();
        }

        static public HashSet<String> eventsToSet(int events) {
            HashSet<String> set = new HashSet<String>();
            while (events != 0) {
                int lowestEvent = Integer.lowestOneBit(events);

                set.add(event2str(lowestEvent));
                events &= ~lowestEvent;
            }
            return set;
        }

        static public String event2str(int event) {
            switch (event) {
                case FileObserver.ACCESS:
                    return "ACCESS";
                case FileObserver.ATTRIB:
                    return "ATTRIB";
                case FileObserver.CLOSE_NOWRITE:
                    return "CLOSE_NOWRITE";
                case FileObserver.CLOSE_WRITE:
                    return "CLOSE_WRITE";
                case FileObserver.CREATE:
                    return "CREATE";
                case FileObserver.DELETE:
                    return "DELETE";
                case FileObserver.DELETE_SELF:
                    return "DELETE_SELF";
                case FileObserver.MODIFY:
                    return "MODIFY";
                case FileObserver.MOVED_FROM:
                    return "MOVED_FROM";
                case FileObserver.MOVED_TO:
                    return "MOVED_TO";
                case FileObserver.MOVE_SELF:
                    return "MOVE_SELF";
                case FileObserver.OPEN:
                    return "OPEN";
                default:
                    return "???";
            }
        }
    }
}
