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

package android.server.wm.activity.lifecycle;

import static android.server.wm.StateLogger.log;

import android.app.Activity;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Pair;

import java.util.ArrayList;
import java.util.List;

/**
 * Used as a shared log storage of events such as activity lifecycle. Methods must be
 * synchronized to prevent concurrent modification of the log store.
 */
public class EventLog extends ContentProvider {

    interface EventTrackerCallback {
        void onEventObserved();
    }

    /** Identifies the activity to which the event corresponds. */
    private static final String EXTRA_KEY_TAG = "key_activity";
    /** Puts a lifecycle or callback into the container. */
    private static final String METHOD_ADD_CALLBACK = "add_callback";

    /**
     * Log for encountered activity callbacks. Note that methods accessing or modifying this
     * list should be synchronized as it can be accessed from different threads.
     */
    private static final List<Pair<String, String>> sLog = new ArrayList<>();

    /**
     * Event tracker interface that waits for correct states or sequences.
     */
    private static EventTrackerCallback sEventTracker;

    /** Clear the entire transition log. */
    public void clear() {
        synchronized (sLog) {
            sLog.clear();
        }
    }

    public void setEventTracker(EventTrackerCallback eventTracker) {
        synchronized (sLog) {
            sEventTracker = eventTracker;
        }
    }

    /** Add activity callback to the log. */
    private void onActivityCallback(String activityCanonicalName, String callback) {
        synchronized (sLog) {
            sLog.add(new Pair<>(activityCanonicalName, callback));
        }
        log("Activity " + activityCanonicalName + " receiver callback " + callback);
        // Trigger check for valid state in the tracker
        if (sEventTracker != null) {
            sEventTracker.onEventObserved();
        }
    }

    /** Get logs for all recorded transitions. */
    public List<Pair<String, String>> getLog() {
        // Wrap in a new list to prevent concurrent modification
        synchronized (sLog) {
            return new ArrayList<>(sLog);
        }
    }

    /** Get transition logs for the specified activity. */
    List<String> getActivityLog(Class<? extends Activity> activityClass) {
        final String activityName = activityClass.getCanonicalName();
        log("Looking up log for activity: " + activityName);
        final List<String> activityLog = new ArrayList<>();
        synchronized (sLog) {
            for (Pair<String, String> transition : sLog) {
                if (transition.first.equals(activityName)) {
                    activityLog.add(transition.second);
                }
            }
        }
        return activityLog;
    }


    // ContentProvider implementation for cross-process tracking

    public static class EventLogClient implements AutoCloseable {
        private static final String EMPTY_ARG = "";
        private final ContentProviderClient mClient;
        private final String mTag;

        public EventLogClient(ContentProviderClient client, String tag) {
            mClient = client;
            mTag = tag;
        }

        public void onCallback(String callback) {
            onCallback(callback, mTag);
        }

        public void onCallback(String callback, Activity activity) {
            onCallback(callback, activity.getClass().getCanonicalName());
        }

        public void onCallback(String callback, String tag) {
            final Bundle extras = new Bundle();
            extras.putString(METHOD_ADD_CALLBACK, callback);
            extras.putString(EXTRA_KEY_TAG, tag);
            try {
                mClient.call(METHOD_ADD_CALLBACK, EMPTY_ARG, extras);
            } catch (RemoteException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() {
            mClient.close();
        }

        /**
         * @param uri Content provider URI for cross-process event log collecting.
         */
        public static EventLogClient create(String tag, Context context, Uri uri) {
            final ContentProviderClient client = context.getContentResolver()
                    .acquireContentProviderClient(uri);
            if (client == null) {
                throw new RuntimeException("Unable to acquire " + uri);
            }
            return new EventLogClient(client, tag);
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!METHOD_ADD_CALLBACK.equals(method)) {
            throw new UnsupportedOperationException();
        }
        onActivityCallback(extras.getString(EXTRA_KEY_TAG), extras.getString(method));
        return null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
