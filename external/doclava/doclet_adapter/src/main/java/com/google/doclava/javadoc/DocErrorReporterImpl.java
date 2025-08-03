/*
 * Copyright (c) 2003, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.google.doclava.javadoc;

import com.google.doclava.annotation.Unused;
import com.sun.javadoc.DocErrorReporter;
import com.sun.javadoc.SourcePosition;

class DocErrorReporterImpl implements DocErrorReporter {

    /**
     * Print error message and increment error count.
     *
     * @param msg message to print
     */
    @Override
    @Unused
    public void printError(String msg) {
        throw new UnsupportedOperationException("not yet implemented");
        // TODO(nikitai): implement this printError(...).
    }

    /**
     * Print an error message and increment error count.
     *
     * @param pos the position item where the error occurs
     * @param msg message to print
     * @since 1.4
     */
    @Override
    @Unused
    public void printError(SourcePosition pos, String msg) {
        throw new UnsupportedOperationException("not yet implemented");
        // TODO(nikitai): implement this printError(...).
    }

    /**
     * Print warning message and increment warning count.
     *
     * @param msg message to print
     */
    @Override
    @Unused
    public void printWarning(String msg) {
        throw new UnsupportedOperationException("not yet implemented");
        // TODO(nikitai): implement this printWarning(...).
    }

    /**
     * Print warning message and increment warning count.
     *
     * @param pos the position item where the warning occurs
     * @param msg message to print
     * @since 1.4
     */
    @Override
    @Unused
    public void printWarning(SourcePosition pos, String msg) {
        throw new UnsupportedOperationException("not yet implemented");
        // TODO(nikitai): implement this printWarning(...).
    }

    /**
     * Print a message.
     *
     * @param msg message to print
     */
    @Override
    @Unused
    public void printNotice(String msg) {
        throw new UnsupportedOperationException("not yet implemented");
        // TODO(nikitai): implement this printNotice(...).
    }

    /**
     * Print a message.
     *
     * @param pos the position item where the message occurs
     * @param msg message to print
     * @since 1.4
     */
    @Override
    @Unused
    public void printNotice(SourcePosition pos, String msg) {
        throw new UnsupportedOperationException("not yet implemented");
        // TODO(nikitai): implement this printNotice(...).
    }
}
