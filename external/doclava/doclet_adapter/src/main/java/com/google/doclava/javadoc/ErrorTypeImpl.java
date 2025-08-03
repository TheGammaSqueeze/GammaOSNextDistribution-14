/*
 * Copyright (c) 2003, 2023, Oracle and/or its affiliates. All rights reserved.
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
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.javadoc.Type;
import com.sun.javadoc.TypeVariable;
import java.util.Objects;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;

class ErrorTypeImpl extends ClassDocImpl {

    private final javax.lang.model.type.ErrorType errorType;

    protected ErrorTypeImpl(TypeElement el, javax.lang.model.type.ErrorType errorType,
            Context context) {
        super(el, context);
        this.errorType = errorType;
    }

    static ErrorTypeImpl create(javax.lang.model.type.ErrorType errorType,
            Context context) {
        var typeEl = (TypeElement) errorType.asElement();
        var typeImpl = context.caches.classes.computeIfAbsent(typeEl,
                el -> new ErrorTypeImpl(el, errorType, context));

        // On rare occasions it can happen that the errorType had already been cached as a
        // ClassDocImpl instead of ErrorTypeImpl. In that case recreate the ErrorTypeImpl and store
        // it in the cache.
        if (!(typeImpl instanceof ErrorTypeImpl)) {
            typeImpl = new ErrorTypeImpl(typeEl, errorType, context);
            context.caches.classes.put(typeEl, typeImpl);
        }

        return (ErrorTypeImpl)typeImpl;
    }

    @Override
    @Used(implemented = true)
    public boolean isIncluded() {
        return false;
    }
}
