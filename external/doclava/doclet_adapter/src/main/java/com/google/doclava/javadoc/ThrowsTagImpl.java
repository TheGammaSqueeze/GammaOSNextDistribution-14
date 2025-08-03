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
import com.google.doclava.annotation.Used;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ThrowsTag;
import com.sun.javadoc.Type;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.ThrowsTree;
import java.util.HashMap;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

class ThrowsTagImpl extends TagImpl implements ThrowsTag {

    private final ThrowsTree throwsTree;

    protected ThrowsTagImpl(ThrowsTree throwsTree, Element owner, Context context) {
        super(throwsTree, owner, context);

        this.throwsTree = throwsTree;
    }

    static ThrowsTagImpl create(ThrowsTree throwsTree, Element owner, Context context) {
        var tagsOfElement = context.caches.tags.throwz.computeIfAbsent(owner,
                el -> new HashMap<>());
        return tagsOfElement.computeIfAbsent(throwsTree,
                el -> new ThrowsTagImpl(el, owner, context));
    }

    private boolean exceptionNameInitialised;
    private String exceptionName;

    @Override
    @Used(implemented = true)
    public String exceptionName() {
        if (!exceptionNameInitialised) {
            var ref = throwsTree.getExceptionName();
            if (ref != null) {
                String signature = ref.getSignature();
                var lastDotPos = signature.lastIndexOf(".");
                exceptionName = signature.substring(lastDotPos + 1);
            }
            exceptionNameInitialised = true;
        }
        return exceptionName;
    }

    private boolean exceptionCommentInitialised;
    private String exceptionComment;

    @Override
    @Used(implemented = true)
    public String exceptionComment() {
        if (!exceptionCommentInitialised) {
            var desc = throwsTree.getDescription();
            if (desc != null) {
                exceptionComment = desc.stream()
                        .map(DocTree::toString)
                        .collect(Collectors.joining(" "));
            }
            exceptionCommentInitialised = true;
        }
        return exceptionComment;
    }

    private boolean exceptionInitialised;
    private ClassDocImpl exception;

    @Override
    @Used(implemented = true)
    public ClassDoc exception() {
        if (!exceptionInitialised) {
            var ref = throwsTree.getExceptionName();
            if (ref != null) {
                String signature = ref.getSignature();
                if (signature != null) {
                    TypeElement te =
                            context.environment.getElementUtils().getTypeElement(signature);
                    if (te != null) {
                        exception = ClassDocImpl.create(te, context);
                    }
                }
            }
            exceptionInitialised = true;
        }
        return exception;
    }

    @Override
    @Unused
    public Type exceptionType() {
        throw new UnsupportedOperationException("not yet implemented");
    }
}
