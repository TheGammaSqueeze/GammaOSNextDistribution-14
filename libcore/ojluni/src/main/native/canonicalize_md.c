/*
 * Copyright (c) 1994, 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * Pathname canonicalization for Unix file systems
 */

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/stat.h>
#include <errno.h>
#include <limits.h>
#include <unistd.h>
#if !defined(_ALLBSD_SOURCE)
#include <alloca.h>
#endif


/* Note: The comments in this file use the terminology
         defined in the java.io.File class */


// BEGIN Android-added: Remove consecutive duplicate path separators "//". b/267617531
// and the trailing path separator `/` if it's not root fs.
char* removeDupSeparator(char *path)
{
    if (path == NULL || *path == '\0') {
        return NULL;
    }

    char *in = path;
    char *out = path;
    char prevChar = 0;
    int n = 0;
    for (; *in != '\0'; in++) {
        // Remove duplicate path separators
        if (!(*in == '/' && prevChar == '/')) {
            *(out++) = *in;
            n++;
        }
        prevChar = *in;
    }
    *out = '\0';

    // Remove the trailing path separator, except when path equals `/`
    if (prevChar == '/' && n > 1) {
        *(--out) = '\0';
    }

    return path;
}
// END Android-added: Remove consecutive duplicate path separators "//". b/267617531

/* Check the given name sequence to see if it can be further collapsed.
   Return zero if not, otherwise return the number of names in the sequence. */

static int
collapsible(char *names)
{
    char *p = names;
    int dots = 0, n = 0;

    while (*p) {
        if ((p[0] == '.') && ((p[1] == '\0')
                              || (p[1] == '/')
                              || ((p[1] == '.') && ((p[2] == '\0')
                                                    || (p[2] == '/'))))) {
            dots = 1;
        }
        n++;
        while (*p) {
            if (*p == '/') {
                // Android-changed: Remove consecutive duplicate path separators "//". b/267617531
                // p++
                while (*p == '/') {
                    p++;
                }
                break;
            }
            p++;
        }
    }
    return (dots ? n : 0);
}


/* Split the names in the given name sequence,
   replacing slashes with nulls and filling in the given index array */

static void
splitNames(char *names, char **ix)
{
    char *p = names;
    int i = 0;

    while (*p) {
        ix[i++] = p++;
        while (*p) {
            if (*p == '/') {
                // Android-changed: Remove consecutive duplicate path separators "//". b/267617531
                //  *p++ = '\0';
                while (*p == '/') {
                    *p++ = '\0';
                }
                break;
            }
            p++;
        }
    }
}


/* Join the names in the given name sequence, ignoring names whose index
   entries have been cleared and replacing nulls with slashes as needed */

static void
joinNames(char *names, int nc, char **ix)
{
    int i;
    char *p;

    for (i = 0, p = names; i < nc; i++) {
        if (!ix[i]) continue;
        if (i > 0) {
            p[-1] = '/';
        }
        if (p == ix[i]) {
            p += strlen(p) + 1;
        } else {
            char *q = ix[i];
            while ((*p++ = *q++));
        }
    }
    *p = '\0';
}


/* Collapse "." and ".." names in the given path wherever possible.
   A "." name may always be eliminated; a ".." name may be eliminated if it
   follows a name that is neither "." nor "..".  This is a syntactic operation
   that performs no filesystem queries, so it should only be used to cleanup
   after invoking the realpath() procedure. */

static void
collapse(char *path)
{
    // Android-changed: Remove consecutive duplicate path separators "//". b/267617531
    removeDupSeparator(path);

    char *names = (path[0] == '/') ? path + 1 : path; /* Preserve first '/' */
    int nc;
    char **ix;
    int i, j;
    // Android-removed: unused variables.
    // char *p, *q;

    nc = collapsible(names);
    if (nc < 2) return;         /* Nothing to do */
    ix = (char **)alloca(nc * sizeof(char *));
    splitNames(names, ix);

    for (i = 0; i < nc; i++) {
        int dots = 0;

        /* Find next occurrence of "." or ".." */
        do {
            char *p = ix[i];
            // Android-changed: null pointer check.
            // if (p[0] == '.') {
            if (p != NULL && p[0] == '.') {
                if (p[1] == '\0') {
                    dots = 1;
                    break;
                }
                if ((p[1] == '.') && (p[2] == '\0')) {
                    dots = 2;
                    break;
                }
            }
            i++;
        } while (i < nc);
        if (i >= nc) break;

        /* At this point i is the index of either a "." or a "..", so take the
           appropriate action and then continue the outer loop */
        if (dots == 1) {
            /* Remove this instance of "." */
            ix[i] = 0;
        }
        else {
            /* If there is a preceding name, remove both that name and this
               instance of ".."; otherwise, leave the ".." as is */
            for (j = i - 1; j >= 0; j--) {
                if (ix[j]) break;
            }
            if (j < 0) continue;
            ix[j] = 0;
            ix[i] = 0;
        }
        /* i will be incremented at the top of the loop */
    }

    joinNames(names, nc, ix);
}


/* Convert a pathname to canonical form.  The input path is assumed to contain
   no duplicate slashes.  On Solaris we can use realpath() to do most of the
   work, though once that's done we still must collapse any remaining "." and
   ".." names by hand. */

// Android-changed: hidden to avoid conflict with libm (b/135018555)
__attribute__((visibility("hidden")))
int
canonicalize(char *original, char *resolved, int len)
{
    if (len < PATH_MAX) {
        errno = EINVAL;
        return -1;
    }

    // Android-changed: Avoid crash in getCanonicalPath() due to a long path. b/266432364
    // if (strlen(original) > PATH_MAX) {
    if (strlen(original) >= PATH_MAX) {
        errno = ENAMETOOLONG;
        return -1;
    }

    /* First try realpath() on the entire path */
    if (realpath(original, resolved)) {
        /* That worked, so return it */
        collapse(resolved);
        return 0;
    }
    else {
        // Android-changed: Avoid crash in getCanonicalPath(). b/266432364
        if (errno == EINVAL || errno == ELOOP || errno == ENAMETOOLONG || errno == ENOMEM) {
            return -1;
        }

        /* Something's bogus in the original path, so remove names from the end
           until either some subpath works or we run out of names */
        char *p, *end, *r = NULL;
        // Android-changed: Avoid crash in getCanonicalPath() due to a long path. b/266432364
        char path[PATH_MAX];

        strncpy(path, original, sizeof(path));
        // Android-changed: Avoid crash in getCanonicalPath() due to a long path. b/266432364
        if (path[PATH_MAX - 1] != '\0') {
            errno = ENAMETOOLONG;
            return -1;
        }
        end = path + strlen(path);

        for (p = end; p > path;) {

            /* Skip last element */
            while ((--p > path) && (*p != '/'));
            if (p == path) break;

            /* Try realpath() on this subpath */
            *p = '\0';
            r = realpath(path, resolved);
            *p = (p == end) ? '\0' : '/';

            if (r != NULL) {
                /* The subpath has a canonical path */
                break;
            }
            // Android-changed: Added ENOTCONN case (b/26645585, b/26070583)
            else if (errno == ENOENT || errno == ENOTDIR || errno == EACCES || errno == ENOTCONN) {
                /* If the lookup of a particular subpath fails because the file
                   does not exist, because it is of the wrong type, or because
                   access is denied, then remove its last name and try again.
                   Other I/O problems cause an error return. */

                /* NOTE: ENOTCONN seems like an odd errno to expect, but this is
                   the behaviour on linux for fuse filesystems when the fuse device
                   associated with the FS is closed but the filesystem is not
                   unmounted. */
                continue;
            }
            else {
                return -1;
            }
        }

        size_t nameMax;
        if (r != NULL) {
            /* Append unresolved subpath to resolved subpath */
            int rn = strlen(r);
            if (rn + (int)strlen(p) >= len) {
                /* Buffer overflow */
                errno = ENAMETOOLONG;
                return -1;
            }

            // Android-changed: Avoid crash in getCanonicalPath() due to a long path. b/266432364
            nameMax = pathconf(r, _PC_NAME_MAX);

            if ((rn > 0) && (r[rn - 1] == '/') && (*p == '/')) {
                /* Avoid duplicate slashes */
                p++;
            }
            strcpy(r + rn, p);
            collapse(r);
        }
        else {
            /* Nothing resolved, so just return the original path */
            // Android-changed: Avoid crash in getCanonicalPath() due to a long path. b/266432364
            nameMax = pathconf("/", _PC_NAME_MAX);
            strcpy(resolved, path);
            collapse(resolved);
        }

        // BEGIN Android-added: Avoid crash in getCanonicalPath() due to a long path. b/266432364
        // Ensure resolve path length is "< PATH_MAX" and collapse() did not overwrite
        // terminating null byte
        char resolvedPath[PATH_MAX];
        strncpy(resolvedPath, resolved, sizeof(resolvedPath));
        if (resolvedPath[PATH_MAX - 1] != '\0') {
            errno = ENAMETOOLONG;
            return -1;
        }

        // Ensure resolve path does not contain any components who length is "> NAME_MAX"
        // If pathconf call failed with -1 or returned 0 in case of permission denial
        if (nameMax < 1) {
            nameMax = NAME_MAX;
        }

        char *component;
        char *rest = resolvedPath;
        while ((component = strtok_r(rest, "/", &rest))) {
            if (strlen(component) > nameMax) {
                errno = ENAMETOOLONG;
                return -1;
            }
        }

        return 0;
        // END Android-added: Avoid crash in getCanonicalPath() due to a long path. b/266432364
    }

}
