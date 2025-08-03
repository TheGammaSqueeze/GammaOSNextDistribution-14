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
import com.sun.javadoc.AnnotationTypeDoc;
import com.sun.javadoc.AnnotationTypeElementDoc;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;

class AnnotationTypeDocImpl extends ClassDocImpl implements AnnotationTypeDoc {

    protected AnnotationTypeDocImpl(TypeElement c, Context context) {
        super(c, context);
    }

    // Cached fields
    private AnnotationTypeElementDoc[] elements;

    static AnnotationTypeDocImpl create(TypeElement e, Context context) {
        if (e.getKind() != ElementKind.ANNOTATION_TYPE && e.getKind() != ElementKind.CLASS) {
            throw new IllegalArgumentException("Expected ElementKind.ANNOTATION_TYPE or "
                    + "ElementKind.CLASS as first argument, but got " + e.getKind());
        }
        return context.caches.annotations.computeIfAbsent(e, el -> new AnnotationTypeDocImpl(el,
                context));
    }

    @Override
    @Used(implemented = true)
    public AnnotationTypeElementDoc[] elements() {
        if (elements == null) {
            elements = ElementFilter.methodsIn(typeElement.getEnclosedElements())
                    .stream()
                    .map(exe -> AnnotationMethodDocImpl.create(exe, context))
                    .toArray(AnnotationTypeElementDoc[]::new);
        }
        return elements;
    }

    @Override
    @Used(implemented = true)
    public boolean isAnnotationType() {
        return true;
    }

    @Override
    @Used(implemented = true)
    public boolean isInterface() {
        return false;
    }

    @Override
    @Unused(implemented = true)
    public AnnotationTypeDoc asAnnotationTypeDoc() {
        return this;
    }
}
