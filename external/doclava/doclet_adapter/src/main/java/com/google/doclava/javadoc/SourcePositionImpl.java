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

import com.google.doclava.annotation.Used;
import com.sun.javadoc.SourcePosition;
import com.sun.source.doctree.DocCommentTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.util.DocSourcePositions;
import com.sun.source.util.DocTrees;
import com.sun.source.util.TreePath;

import java.io.File;
import java.io.IOException;

import javax.lang.model.element.Element;
import javax.tools.JavaFileObject;

import jdk.javadoc.doclet.DocletEnvironment;

class SourcePositionImpl implements SourcePosition {

    public static final SourcePositionImpl UNKNOWN = new SourcePositionImpl();

    private static final int POS_NOT_FOUND = -1;
    private static final int POS_NULL_SOURCE_POSITIONS = -100;
    private static final int POS_NULL_COMPILATION_UNIT_TREE = -200;
    private static final int POS_FAILED = -300;

    private final File file;
    private final int line;
    private final int column;


    static SourcePosition create(Context context, Element element) {
        return create(context, element, null);
    }

    static SourcePosition create(Context context, Element element, DocTree docTree) {
        try {
            Builder builder = new Builder(context, element, docTree);
            return builder.build();
        } catch (Exception e) {
            return SourcePositionImpl.UNKNOWN;
        }
    }

    private static class Builder {
        private final Context context;
        private final Element element;
        private DocTree docTree;

        private boolean initialized = false;
        private DocTrees docTrees;
        private TreePath treePath;
        private DocCommentTree docCommentTree;
        private CompilationUnitTree compilationUnitTree;
        private JavaFileObject javaFileObject;
        private DocSourcePositions sourcePositions;

        Builder(Context context, Element element, DocTree docTree) {
            this.context = context;
            this.element = element;
            this.docTree = docTree;
        }

        SourcePosition build() {
            if (!init()) {
                return SourcePositionImpl.UNKNOWN;
            }
            long pos = getSourcePosition();
            if (pos < 0) {
                return createPosError(pos);
            }
            PositionConverter conv = new PositionConverter(javaFileObject, pos);
            return new SourcePositionImpl(javaFileObject.getName(), conv.getLine(), conv.getCol());
        }

        private boolean init() {
            if (initialized) {
                return true;
            }
            docTrees = context.environment.getDocTrees();
            treePath = docTrees.getPath(element);
            if (treePath == null) {
                return false;
            }
            docCommentTree = docTrees.getDocCommentTree(element);
            if (docCommentTree == null) {
                return false;
            }
            compilationUnitTree = treePath.getCompilationUnit();
            javaFileObject = compilationUnitTree.getSourceFile();
            sourcePositions = docTrees.getSourcePositions();
            if (docTree == null) {
                docTree = docCommentTree;
            }
            initialized = true;
            return true;
        }

        private SourcePosition createPosError(long code) {
            return new SourcePositionImpl(javaFileObject.getName(), (int)code, 0);
        }

        private long getSourcePosition() {
            if (sourcePositions == null) {
                return POS_NULL_SOURCE_POSITIONS;
            }
            if (compilationUnitTree == null) {
                return POS_NULL_COMPILATION_UNIT_TREE;
            }
            return sourcePositions.getStartPosition(compilationUnitTree,
                    docCommentTree, docTree);
        }
    }

    private SourcePositionImpl() {
        this.file = new File(".");
        this.line = POS_NOT_FOUND;
        this.column = 0;
    }

    private SourcePositionImpl(String filename, int line, int column) {
        this.file = new File(filename);
        this.line = line;
        this.column = column;
    }

    @Override
    @Used(implemented = true)
    public File file() {
        return file;
    }

    @Override
    @Used(implemented = true)
    public int line() {
        return line;
    }

    @Override
    @Used(implemented = true)
    public int column() {
        return column;
    }

    private static class PositionConverter {
        final JavaFileObject javaFileObject;
        final long pos;
        int lineStartAt = -1;
        int line = -1;

        public PositionConverter(JavaFileObject javaFileObject, long pos) {
            this.javaFileObject = javaFileObject;
            this.pos = pos;
        }

        public int getLine() {
            if (pos < 0) {
                return (int) pos;
            }

            if (!findLine()) {
                return POS_FAILED;
            }

            return line;
        }

        private boolean findLine() {
            if (line > 0) {
                return true;
            }
            try {
                char[] buf = loadBuf(javaFileObject);
                if (pos > buf.length) {
                    return false;
                }
                lineStartAt = 0;
                line = 1;

                for (int idx = 0; idx < buf.length && idx < pos; idx++) {
                    if (isEndOfLine(idx, buf)) {
                        line++;
                        lineStartAt = idx+1;
                    }
                }
                return true;
            } catch (IOException e) {
                line = -1;
                lineStartAt = -1;
                return false;
            }
        }

        private char[] loadBuf(JavaFileObject fileObject) throws IOException {
            CharSequence chars = fileObject.getCharContent(true);
            return chars.toString().toCharArray();
        }

        private boolean isEndOfLine(int idx, char[] buf) {
            if (buf[idx] == '\n') {
                return true;
            }
            if (buf[idx] == '\r' && (idx == buf.length - 1 || buf[idx+1] != '\n')) {
                return true;
            }
            return false;
        }

        public int getCol() {
            if (pos < 0) {
                return -1;
            }

            if (!findLine()) {
                return 0;
            }

            return (int)pos - lineStartAt + 1;
        }
    }
}
