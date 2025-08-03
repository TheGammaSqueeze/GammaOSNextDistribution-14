/*
 * Copyright (C) 2017-2022 The LineageOS Project
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

package org.lineageos.tvcustomizer;

import android.content.ContentProvider;
import android.content.ContentProvider.PipeDataWriter;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class ConfigurationProvider extends ContentProvider {
    private static final UriMatcher sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final String TAG = "ConfigurationProvider";
    private static final int TVLAUNCHER_CONFIG = 1;

    static {
        sUriMatcher.addURI("tvlauncher.config", "configuration", TVLAUNCHER_CONFIG);
    }

    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        InputStream stream;
        Log.d(TAG, "openFile called: " + uri + "  by: " + getCallingPackage());
        if (sUriMatcher.match(uri) == TVLAUNCHER_CONFIG) {
            stream = getContext().getResources().openRawResource(R.raw.configuration);
        } else {
            Log.d(TAG, "Invalid URI");
            throw new UnsupportedOperationException("Unknown uri: " + uri);
        }
        return openPipeHelper(uri, "text/xml", null, null, new PipeDataWriter() {
            public void writeDataToPipe(ParcelFileDescriptor output, Uri uri, String mimeType, Bundle opts, Object args) {
                FileOutputStream out = new FileOutputStream(output.getFileDescriptor());
                try {
                    int count;
                    byte[] buffer = new byte[8192];
                    while (true) {
                        count = stream.read(buffer);
                        if (count == -1) {
                            break;
                        }
                        Log.d(TAG, "Written: " + count + " bytes");
                        out.write(buffer, 0, count);
                    }
                    Log.d(TAG, "Written: " + count + " bytes");
                } catch (IOException e) {
                    Log.e(TAG, "Failed to send file " + uri, e);
                    throw e;
                } finally {
                    try {
                        if (out != null) {
                            out.close();
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to close file " + uri, e);
                    }
                    return;
                }
            }
        });
    }

    @Override
    public boolean onCreate() {
        Log.d(TAG, "onCreate called: " + getCallingPackage());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        Log.d(TAG, "query called: " + uri);
        sUriMatcher.match(uri);
        throw new IllegalArgumentException("Unknown URI: " + uri);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        Log.d(TAG, "insert called: " + uri);
        throw new UnsupportedOperationException();
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        Log.d(TAG, "delete called: " + uri);
        throw new UnsupportedOperationException();
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        Log.d(TAG, "update called: " + uri);
        throw new UnsupportedOperationException();
    }

    @Override
    public String getType(Uri uri) {
        Log.d(TAG, "getType called: " + uri);
        throw new UnsupportedOperationException();
    }
}
