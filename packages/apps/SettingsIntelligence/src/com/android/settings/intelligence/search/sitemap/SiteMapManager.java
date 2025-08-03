/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.settings.intelligence.search.sitemap;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import androidx.annotation.WorkerThread;
import android.text.TextUtils;
import android.util.Log;

import com.android.settings.intelligence.search.indexing.IndexDatabaseHelper;
import com.android.settings.intelligence.search.indexing.IndexDatabaseHelper.SiteMapColumns;

import java.util.ArrayList;
import java.util.List;

public class SiteMapManager {

    private static final String TAG = "SiteMapManager";
    private static final String TOP_LEVEL_SETTINGS
            = "com.android.settings.homepage.TopLevelSettings";
    private static final boolean DEBUG_TIMING = false;

    public static final String[] SITE_MAP_COLUMNS = {
            SiteMapColumns.PARENT_CLASS,
            SiteMapColumns.PARENT_TITLE,
            SiteMapColumns.CHILD_CLASS,
            SiteMapColumns.CHILD_TITLE,
            SiteMapColumns.HIGHLIGHTABLE_MENU_KEY
    };

    private final List<SiteMapPair> mPairs = new ArrayList<>();

    private boolean mInitialized;

    /**
     * Check whether the specified class is top level settings class.
     */
    public static boolean isTopLevelSettings(String clazz) {
        return TextUtils.equals(TOP_LEVEL_SETTINGS, clazz);
    }

    /**
     * Given a fragment class name and its screen title, build a breadcrumb from Settings root to
     * this screen.
     * <p/>
     * Not all screens have a full breadcrumb path leading up to root, it's because either some
     * page in the breadcrumb path is not indexed, or it's only reachable via search.
     */
    @WorkerThread
    public synchronized List<String> buildBreadCrumb(Context context, String clazz,
            String screenTitle) {
        init(context);
        final long startTime = System.currentTimeMillis();
        final List<String> breadcrumbs = new ArrayList<>();
        if (!mInitialized) {
            Log.w(TAG, "SiteMap is not initialized yet, skipping");
            return breadcrumbs;
        }
        if (!TextUtils.isEmpty(screenTitle)) {
            breadcrumbs.add(screenTitle);
        }
        String currentClass = clazz;
        String currentTitle = screenTitle;
        // Look up current page's parent, if found add it to breadcrumb string list, and repeat.
        while (true) {
            final SiteMapPair pair = lookUpParent(currentClass, currentTitle);
            if (pair == null) {
                if (DEBUG_TIMING) {
                    Log.d(TAG, "BreadCrumb timing: " + (System.currentTimeMillis() - startTime));
                }
                return breadcrumbs;
            }
            final String parentTitle = pair.getParentTitle();
            if (!TextUtils.isEmpty(parentTitle)) {
                breadcrumbs.add(0, parentTitle);
            }
            currentClass = pair.getParentClass();
            currentTitle = pair.getParentTitle();
        }
    }

    public synchronized SiteMapPair getTopLevelPair(Context context, String clazz,
            String screenTitle) {
        if (!mInitialized) {
            init(context);
        }

        // find the default pair
        SiteMapPair currentPair = null;
        if (!TextUtils.isEmpty(clazz)) {
            for (SiteMapPair pair : mPairs) {
                if (TextUtils.equals(pair.getChildClass(), clazz)) {
                    currentPair = pair;
                    if (TextUtils.isEmpty(screenTitle)) {
                        screenTitle = pair.getChildTitle();
                    }
                    break;
                }
            }
        }

        // recursively find the top level pair
        String currentClass = clazz;
        String currentTitle = screenTitle;
        while (true) {
            // look up parent by class and title
            SiteMapPair pair = lookUpParent(currentClass, currentTitle);
            if (pair == null) {
                // fallback option: look up parent only by title
                pair = lookUpParent(currentTitle);
                if (pair == null) {
                    return currentPair;
                }
            }
            if (!TextUtils.isEmpty(pair.getHighlightableMenuKey())) {
                return pair;
            }
            currentPair = pair;
            currentClass = pair.getParentClass();
            currentTitle = pair.getParentTitle();
        }
    }

    /**
     * Initialize a list of {@link SiteMapPair}s. Each pair knows about a single parent-child
     * page relationship.
     */
    @WorkerThread
    private synchronized void init(Context context) {
        if (mInitialized) {
            // Make sure only init once.
            return;
        }

        // Will init again if site map table updated, need clear the old data if it's not empty.
        if (!mPairs.isEmpty()) {
            mPairs.clear();
        }

        final long startTime = System.currentTimeMillis();
        // First load site map from static index table.
        final Context appContext = context.getApplicationContext();
        final SQLiteDatabase db = IndexDatabaseHelper.getInstance(appContext).getReadableDatabase();
        Cursor sitemap = db.query(IndexDatabaseHelper.Tables.TABLE_SITE_MAP, SITE_MAP_COLUMNS, null,
                null, null, null, null);
        while (sitemap.moveToNext()) {
            final SiteMapPair pair = new SiteMapPair(
                    sitemap.getString(sitemap.getColumnIndex(SiteMapColumns.PARENT_CLASS)),
                    sitemap.getString(sitemap.getColumnIndex(SiteMapColumns.PARENT_TITLE)),
                    sitemap.getString(sitemap.getColumnIndex(SiteMapColumns.CHILD_CLASS)),
                    sitemap.getString(sitemap.getColumnIndex(SiteMapColumns.CHILD_TITLE)),
                    sitemap.getString(sitemap.getColumnIndex(SiteMapColumns.HIGHLIGHTABLE_MENU_KEY))
            );
            mPairs.add(pair);
        }
        sitemap.close();
        // Done.
        mInitialized = true;
        if (DEBUG_TIMING) {
            Log.d(TAG, "Init timing: " + (System.currentTimeMillis() - startTime));
        }
    }

    @WorkerThread
    private SiteMapPair lookUpParent(String clazz, String title) {
        for (SiteMapPair pair : mPairs) {
            if (TextUtils.equals(pair.getChildClass(), clazz)
                    && TextUtils.equals(title, pair.getChildTitle())) {
                return pair;
            }
        }
        return null;
    }

    @WorkerThread
    private SiteMapPair lookUpParent(String title) {
        if (TextUtils.isEmpty(title)) {
            return null;
        }
        for (SiteMapPair pair : mPairs) {
            if (TextUtils.equals(title, pair.getChildTitle())) {
                return pair;
            }
        }
        return null;
    }

    @WorkerThread
    public void setInitialized(boolean initialized) {
        mInitialized = initialized;
    }
}
