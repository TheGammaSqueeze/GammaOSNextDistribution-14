/*
 * Copyright (C) 2026 GammaOS
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
package com.gammaos.shares;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.internal.content.FileSystemProvider;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Makes mounted network shares reachable from the system file picker.
 *
 * The shares are already ordinary directories under /mnt/shares, served by the gammaos-sharefs FUSE
 * daemon, and the GammaOS Nano menu reads them as plain paths. Apps cannot: they are handed storage
 * through the Storage Access Framework rather than by path, so without a provider a share is
 * invisible to every app on the device even though it is sitting right there on the filesystem.
 * This publishes one SAF root per mounted share, which is what makes "open a file from my NAS" work
 * in an arbitrary app.
 *
 * <p>Almost all of the behaviour comes from {@link FileSystemProvider}, the same base
 * ExternalStorageProvider uses, so listing, opening, creating, renaming and deleting all follow the
 * platform's own semantics rather than a reimplementation of them.
 *
 * <p>The roots are discovered from the kernel mount table rather than by listing /mnt/shares. The
 * daemon creates its mount point before it connects, so a directory being there does not mean a
 * usable share, and stat-ing a mount whose server has gone away blocks until FUSE times out.
 * Reading /proc/self/mountinfo costs nothing and never touches the network. This mirrors what the
 * menu does in NanoMenuPS3Folder.cpp.
 */
public class ShareDocumentsProvider extends FileSystemProvider {
    private static final String TAG = "GammaShares";

    private static final String AUTHORITY = "com.gammaos.shares.documents";
    private static final File SHARE_ROOT = new File("/mnt/shares");

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] {
            Root.COLUMN_ROOT_ID, Root.COLUMN_FLAGS, Root.COLUMN_ICON,
            Root.COLUMN_TITLE, Root.COLUMN_SUMMARY, Root.COLUMN_DOCUMENT_ID,
    };
    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] {
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
    };

    @Override
    public boolean onCreate() {
        super.onCreate(DEFAULT_DOCUMENT_PROJECTION);
        return true;
    }

    // ---- roots ---------------------------------------------------------------

    /**
     * The share names that are mounted right now, read from the kernel mount table.
     *
     * <p>Mount points are escaped in mountinfo (a space becomes \040), and the default share names
     * are "Share 1", "Share 2"... so unescaping is not optional here.
     */
    private static List<String> mountedShareNames() {
        final List<String> names = new ArrayList<>();
        try {
            for (String line : Files.readAllLines(Paths.get("/proc/self/mountinfo"),
                    StandardCharsets.UTF_8)) {
                // Field 5 (1-based) is the mount point.
                final String[] f = line.split(" ");
                if (f.length < 5) continue;
                final String mp = unescape(f[4]);
                if (!mp.startsWith("/mnt/shares/")) continue;
                final String name = mp.substring("/mnt/shares/".length());
                // Only the share's own mount point, nothing nested below it.
                if (name.isEmpty() || name.indexOf('/') >= 0) continue;
                if (!names.contains(name)) names.add(name);
            }
        } catch (IOException e) {
            Log.w(TAG, "cannot read the mount table, no shares will be offered", e);
        }
        return names;
    }

    private static String unescape(String s) {
        if (s.indexOf('\\') < 0) return s;
        final StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '\\' && i + 3 < s.length()) {
                try {
                    out.append((char) Integer.parseInt(s.substring(i + 1, i + 4), 8));
                    i += 3;
                    continue;
                } catch (NumberFormatException ignored) {
                    // Not an octal escape after all; fall through and keep the backslash.
                }
            }
            out.append(s.charAt(i));
        }
        return out.toString();
    }

    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException {
        final MatrixCursor result = new MatrixCursor(
                projection != null ? projection : DEFAULT_ROOT_PROJECTION);
        for (String name : mountedShareNames()) {
            final File dir = new File(SHARE_ROOT, name);
            final MatrixCursor.RowBuilder row = result.newRow();
            row.add(Root.COLUMN_ROOT_ID, name);
            row.add(Root.COLUMN_DOCUMENT_ID, getDocIdForFile(dir));
            row.add(Root.COLUMN_TITLE, name);
            row.add(Root.COLUMN_SUMMARY, "Network share");
            row.add(Root.COLUMN_ICON, android.R.drawable.ic_menu_share);
            // LOCAL_ONLY is deliberately NOT set: this really is remote storage, and an app that
            // asks for local-only content should not be offered a NAS it may stall on.
            int flags = Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_IS_CHILD
                    | Root.FLAG_SUPPORTS_SEARCH;
            if (!dir.canWrite()) {
                // A read-only share, or one whose server is refusing writes: say so rather than
                // offering a Create button that will fail.
                flags &= ~Root.FLAG_SUPPORTS_CREATE;
            }
            row.add(Root.COLUMN_FLAGS, flags);
        }
        return result;
    }

    // ---- document id <-> file ------------------------------------------------

    @Override
    protected String getDocIdForFile(File file) {
        // The absolute path is the document id. Every share lives under one fixed prefix that the
        // caller cannot influence, and getFileForDocId re-checks containment, so a crafted id
        // cannot walk out of /mnt/shares.
        return file.getAbsolutePath();
    }

    @Override
    protected File getFileForDocId(String docId, boolean visible) throws FileNotFoundException {
        final File target = new File(docId);
        final String canonical;
        final String rootCanonical;
        try {
            canonical = target.getCanonicalPath();
            rootCanonical = SHARE_ROOT.getCanonicalPath();
        } catch (IOException e) {
            throw new FileNotFoundException("cannot resolve " + docId);
        }
        // Containment check on the CANONICAL path, so neither "../" nor a symlink on the share can
        // hand an app something outside /mnt/shares.
        if (!canonical.equals(rootCanonical) && !canonical.startsWith(rootCanonical + "/")) {
            throw new FileNotFoundException(docId + " is outside " + SHARE_ROOT);
        }
        return target;
    }

    @Override
    protected Uri buildNotificationUri(String docId) {
        return DocumentsContract.buildChildDocumentsUri(AUTHORITY, docId);
    }

    // ---- documents -----------------------------------------------------------

    /**
     * Search within one share.
     *
     * queryRoots advertises FLAG_SUPPORTS_SEARCH, and a capability advertised but not implemented
     * makes the picker's search box fail rather than simply not appear. FileSystemProvider does the
     * walking; all this has to do is turn a root id back into the directory to walk.
     *
     * <p>Note this walks the share over the network, so it is as slow as the server is. That is
     * inherent rather than a defect: the alternative is an index that would be stale the moment
     * another machine wrote to the share.
     */
    @Override
    public Cursor querySearchDocuments(String rootId, String[] projection, Bundle queryArgs)
            throws FileNotFoundException {
        final File parent = new File(SHARE_ROOT, rootId);
        return querySearchDocuments(parent,
                projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION,
                Collections.emptySet(), queryArgs);
    }

}
