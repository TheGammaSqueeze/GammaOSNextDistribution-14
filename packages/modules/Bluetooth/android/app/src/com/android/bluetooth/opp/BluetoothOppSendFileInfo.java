/*
 * Copyright (c) 2008-2009, Motorola, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * - Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * - Neither the name of the Motorola, Inc. nor the names of its contributors
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.bluetooth.opp;

import static android.os.UserHandle.myUserId;

import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothProtoEnums;
import android.content.ContentResolver;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.util.EventLog;
import android.util.Log;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.BluetoothStatsLog;
import com.android.bluetooth.R;
import com.android.bluetooth.content_profiles.ContentProfileErrorReportUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Objects;

/**
 * This class stores information about a single sending file It will only be used for outbound
 * share.
 */
// Next tag value for ContentProfileErrorReportUtils.report(): 15
public class BluetoothOppSendFileInfo {
    private static final String TAG = "BluetoothOppSendFileInfo";

    private static final boolean D = Constants.DEBUG;


    /** Reusable SendFileInfo for error status. */
    static final BluetoothOppSendFileInfo SEND_FILE_INFO_ERROR =
            new BluetoothOppSendFileInfo(null, null, 0, null, BluetoothShare.STATUS_FILE_ERROR);

    /** readable media file name */
    public final String mFileName;

    /** media file input stream */
    public final FileInputStream mInputStream;

    /** vCard string data */
    public final String mData;

    public final int mStatus;

    public final String mMimetype;

    public final long mLength;

    /** for media file */
    public BluetoothOppSendFileInfo(String fileName, String type, long length,
            FileInputStream inputStream, int status) {
        mFileName = fileName;
        mMimetype = type;
        mLength = length;
        mInputStream = inputStream;
        mStatus = status;
        mData = null;
    }

    /** for vCard, or later for vCal, vNote. Not used currently */
    public BluetoothOppSendFileInfo(String data, String type, long length, int status) {
        mFileName = null;
        mInputStream = null;
        mData = data;
        mMimetype = type;
        mLength = length;
        mStatus = status;
    }

    public static BluetoothOppSendFileInfo generateFileInfo(Context context, Uri uri, String type,
            boolean fromExternal) {
        ContentResolver contentResolver = context.getContentResolver();
        String scheme = uri.getScheme();
        String fileName = null;
        String contentType;
        long length = 0;
        // Support all Uri with "content" scheme
        // This will allow more 3rd party applications to share files via
        // bluetooth
        if ("content".equals(scheme)) {
            if (fromExternal && BluetoothOppUtility.isForbiddenContent(uri)) {
                EventLog.writeEvent(0x534e4554, "179910660", -1, uri.toString());
                Log.e(TAG, "Content from forbidden URI is not allowed.");
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                        0);
                return SEND_FILE_INFO_ERROR;
            }

            if (isContentUriForOtherUser(uri)) {
                Log.e(TAG, "Uri: " + uri + " is invalid for user " + myUserId());
                return SEND_FILE_INFO_ERROR;
            }

            contentType = contentResolver.getType(uri);
            Cursor metadataCursor;
            try {
                metadataCursor = BluetoothMethodProxy.getInstance().contentResolverQuery(
                        contentResolver, uri, new String[]{
                                OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE
                        }, null, null, null);
            } catch (SQLiteException e) {
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                        1);
                // some content providers don't support the DISPLAY_NAME or SIZE columns
                metadataCursor = null;
            } catch (SecurityException e) {
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                        2);
                Log.e(TAG, "generateFileInfo: Permission error, could not access URI: " + uri);
                return SEND_FILE_INFO_ERROR;
            }

            if (metadataCursor != null) {
                try {
                    if (metadataCursor.moveToFirst()) {
                        int indexName = metadataCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                        int indexSize = metadataCursor.getColumnIndex(OpenableColumns.SIZE);
                        if (indexName != -1) {
                            fileName = metadataCursor.getString(indexName);
                        }
                        if (indexSize != -1) {
                            length = metadataCursor.getLong(indexSize);
                        }
                        if (D) {
                            Log.d(TAG, "fileName = " + fileName + " length = " + length);
                        }
                    }
                } finally {
                    metadataCursor.close();
                }
            }
            if (fileName == null) {
                // use last segment of URI if DISPLAY_NAME query fails
                fileName = uri.getLastPathSegment();
                if (D) Log.d(TAG, "fileName from URI :" + fileName);
            }
        } else if ("file".equals(scheme)) {
            if (uri.getPath() == null) {
                Log.e(TAG, "Invalid URI path: " + uri);
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                        3);
                return SEND_FILE_INFO_ERROR;
            }
            if (fromExternal && !BluetoothOppUtility.isInExternalStorageDir(uri)) {
                EventLog.writeEvent(0x534e4554, "35310991", -1, uri.getPath());
                Log.e(TAG, "File based URI not in Environment.getExternalStorageDirectory() is not "
                        + "allowed.");
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                        4);
                return SEND_FILE_INFO_ERROR;
            }
            fileName = uri.getLastPathSegment();
            contentType = type;
            File f = new File(uri.getPath());
            length = f.length();
        } else {
            // currently don't accept other scheme
            return SEND_FILE_INFO_ERROR;
        }
        FileInputStream is = null;
        if (scheme.equals("content")) {
            try {
                // We've found that content providers don't always have the
                // right size in _OpenableColumns.SIZE
                // As a second source of getting the correct file length,
                // get a file descriptor and get the stat length
                AssetFileDescriptor fd = BluetoothMethodProxy.getInstance()
                        .contentResolverOpenAssetFileDescriptor(contentResolver, uri, "r");
                long statLength = fd.getLength();
                if (length != statLength && statLength > 0) {
                    Log.e(TAG, "Content provider length is wrong (" + Long.toString(length)
                            + "), using stat length (" + Long.toString(statLength) + ")");
                    ContentProfileErrorReportUtils.report(
                            BluetoothProfile.OPP,
                            BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                            BluetoothStatsLog
                                    .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                            5);
                    length = statLength;
                }

                try {
                    // This creates an auto-closing input-stream, so
                    // the file descriptor will be closed whenever the InputStream
                    // is closed.
                    is = fd.createInputStream();

                    // If the database doesn't contain the file size, get the size
                    // by reading through the entire stream
                    if (length == 0) {
                        length = getStreamSize(is);
                        Log.w(TAG, "File length not provided. Length from stream = " + length);
                        ContentProfileErrorReportUtils.report(
                                BluetoothProfile.OPP,
                                BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                                BluetoothStatsLog
                                        .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_WARN,
                                6);
                        // Reset the stream
                        fd = BluetoothMethodProxy.getInstance()
                                .contentResolverOpenAssetFileDescriptor(contentResolver, uri, "r");
                        is = fd.createInputStream();
                    }
                } catch (IOException e) {
                    ContentProfileErrorReportUtils.report(
                            BluetoothProfile.OPP,
                            BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                            BluetoothStatsLog
                                    .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                            7);
                    try {
                        fd.close();
                    } catch (IOException e2) {
                        ContentProfileErrorReportUtils.report(
                                BluetoothProfile.OPP,
                                BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                                BluetoothStatsLog
                                        .BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                                8);
                        // Ignore
                    }
                }
            } catch (FileNotFoundException e) {
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                        9);
                // Ignore
            } catch (SecurityException e) {
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                        10);
                return SEND_FILE_INFO_ERROR;
            }
        }

        if (is == null) {
            try {
                is = (FileInputStream) BluetoothMethodProxy.getInstance()
                        .contentResolverOpenInputStream(contentResolver, uri);

                // If the database doesn't contain the file size, get the size
                // by reading through the entire stream
                if (length == 0) {
                    length = getStreamSize(is);
                    // Reset the stream
                    is = (FileInputStream) BluetoothMethodProxy.getInstance()
                            .contentResolverOpenInputStream(contentResolver, uri);
                }
            } catch (FileNotFoundException e) {
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                        11);
                return SEND_FILE_INFO_ERROR;
            } catch (IOException e) {
                ContentProfileErrorReportUtils.report(
                        BluetoothProfile.OPP,
                        BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                        BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__EXCEPTION,
                        12);
                return SEND_FILE_INFO_ERROR;
            }
        }

        if (length == 0) {
            Log.e(TAG, "Could not determine size of file");
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.OPP,
                    BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                    13);
            return SEND_FILE_INFO_ERROR;
        } else if (length > 0xffffffffL) {
            Log.e(TAG, "File of size: " + length + " bytes can't be transferred");
            ContentProfileErrorReportUtils.report(
                    BluetoothProfile.OPP,
                    BluetoothProtoEnums.BLUETOOTH_OPP_SEND_FILE_INFO,
                    BluetoothStatsLog.BLUETOOTH_CONTENT_PROFILE_ERROR_REPORTED__TYPE__LOG_ERROR,
                    14);
            throw new IllegalArgumentException(context
                .getString(R.string.bluetooth_opp_file_limit_exceeded));
        }

        return new BluetoothOppSendFileInfo(fileName, contentType, length, is, 0);
    }

    /**
     * Determine if the given {@link Uri} is a content uri for another user.
     *
     * <p>RFC 2396 s.3.2. states that <tt>'@'</tt> is reserved in the authority component. Its
     * encoded form should be interpreted as data within the authority component. However,
     * ContentProvider APIs use the decoded {@link Uri#getAuthority()} with {@link
     * ContentProvider#getUserIdFromAuthority(String, int)} to determine the <tt>userId</tt> in the
     * userInfo, rather than {@link Uri#getUserInfo()}. An encoded <tt>'@'</tt>, which is
     * <tt>'%40'</tt>, is interpreted by ContentProvider as the separator for userInfo and host.
     *
     * <p>As an unbundled module, Bluetooth cannot access ContentProvider#getUserIdFromAuthority, so
     * parse userInfo here from the authority.
     */
    private static boolean isContentUriForOtherUser(Uri uri) {
        String authority = uri.getAuthority();
        if (authority == null) {
            return false;
        }
        int atIndex = authority.lastIndexOf('@');
        if (atIndex == -1) {
            return false;
        }
        String uriUserId = authority.substring(0, atIndex);
        return !TextUtils.isEmpty(uriUserId)
                && !Objects.equals(uriUserId, String.valueOf(myUserId()));
    }

    private static long getStreamSize(FileInputStream is) throws IOException {
        long length = 0;
        byte[] unused = new byte[4096];
        int bytesRead = is.read(unused, 0, 4096);
        while (bytesRead != -1) {
            length += bytesRead;
            bytesRead = is.read(unused, 0, 4096);
        }
        return length;
    }
}
