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
import com.sun.javadoc.AnnotationDesc;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.ProgramElementDoc;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

abstract class ProgramElementDocImpl<T extends Element> extends DocImpl<T> implements
        ProgramElementDoc {

    /**
     * Direct mapping of {@link javax.lang.model.element.Modifier} to {@link
     * java.lang.reflect.Modifier}.
     *
     * @implNote Can be updated by subclasses ({@link ClassDocImpl} and {@link
     * AnnotationTypeDocImpl} to add {@link java.lang.reflect.Modifier#INTERFACE} modifier.
     * @see javax.lang.model.element.Modifier
     * @see java.lang.reflect.Modifier
     * @see ClassDocImpl
     * @see AnnotationTypeDocImpl
     */
    protected int reflectModifiers;

    protected T element;

    private AnnotationDescImpl[] annotations;

    protected ProgramElementDocImpl(T e, Context context) {
        super(e, context);

        this.element = e;
        this.reflectModifiers = elementModifiersToReflectModifiers(e.getModifiers());
    }

    private int elementModifiersToReflectModifiers(Set<Modifier> modifiers) {
        int mods = 0;
        for (Modifier m : modifiers) {
            switch (m) {
                case ABSTRACT -> mods |= java.lang.reflect.Modifier.ABSTRACT;
                case FINAL -> mods |= java.lang.reflect.Modifier.FINAL;
                case PRIVATE -> mods |= java.lang.reflect.Modifier.PRIVATE;
                case PROTECTED -> mods |= java.lang.reflect.Modifier.PROTECTED;
                case PUBLIC -> mods |= java.lang.reflect.Modifier.PUBLIC;
                case NATIVE -> mods |= java.lang.reflect.Modifier.NATIVE;
                case STATIC -> mods |= java.lang.reflect.Modifier.STATIC;
                case STRICTFP -> mods |= java.lang.reflect.Modifier.STRICT;
                case SYNCHRONIZED -> mods |= java.lang.reflect.Modifier.SYNCHRONIZED;
                case TRANSIENT -> mods |= java.lang.reflect.Modifier.TRANSIENT;
                case VOLATILE -> mods |= java.lang.reflect.Modifier.VOLATILE;
                case DEFAULT, NON_SEALED, SEALED -> {
                    // Exhaust the remaining element modifiers that are not present in
                    // java.lang.reflect.Modifier.
                }
                default -> throw new UnsupportedOperationException("Unexpected modifier: " + m);
            }
        }
        return mods;
    }

    @Override
    @Used(implemented = true)
    public ClassDoc containingClass() {
        Element cur = element.getEnclosingElement();
        while (cur != null && cur.getKind() != ElementKind.ANNOTATION_TYPE
                && cur.getKind() != ElementKind.CLASS
                && cur.getKind() != ElementKind.ENUM
                && cur.getKind() != ElementKind.INTERFACE
                && cur.getKind() != ElementKind.RECORD) {
            cur = cur.getEnclosingElement();
        }
        if (cur == null) {
            return null;
        }
        return switch (cur.getKind()) {
            case CLASS, INTERFACE, ENUM, ANNOTATION_TYPE, RECORD -> ClassDocImpl.create((TypeElement) cur,
                    context);
            default -> null;
        };
    }

    @Override
    @Used(implemented = true)
    public PackageDoc containingPackage() {
        Element cur = element.getEnclosingElement();
        while (cur.getKind() != ElementKind.PACKAGE) {
            cur = cur.getEnclosingElement();
        }
        return PackageDocImpl.create((PackageElement) cur, context);
    }

    @Override
    @Unused(implemented = true)
    public int modifierSpecifier() {
        return reflectModifiers;
    }

    @Override
    @Unused(implemented = true)
    public String modifiers() {
        return java.lang.reflect.Modifier.toString(reflectModifiers);
    }

    @Override
    @Used(implemented = true)
    public AnnotationDesc[] annotations() {
        if (annotations == null) {
            annotations = element.getAnnotationMirrors()
                    .stream()
                    // b/270334687: Filter out elements that are not ANNOTATION_TYPE
                    .filter(am -> ((TypeElement) am.getAnnotationType().asElement()).getKind() == ElementKind.ANNOTATION_TYPE)
                    .map(am -> new AnnotationDescImpl(am, context))
                    .toArray(AnnotationDescImpl[]::new);
        }
        return annotations;
    }

    @Override
    @Used(implemented = true)
    public boolean isPublic() {
        return java.lang.reflect.Modifier.isPublic(reflectModifiers);
    }

    @Override
    @Used(implemented = true)
    public boolean isProtected() {
        return java.lang.reflect.Modifier.isProtected(reflectModifiers);
    }

    @Override
    @Used(implemented = true)
    public boolean isPrivate() {
        return java.lang.reflect.Modifier.isPrivate(reflectModifiers);
    }

    @Override
    @Used(implemented = true)
    public boolean isPackagePrivate() {
        return !(isPublic() || isPrivate() || isProtected());
    }

    @Override
    @Used(implemented = true)
    public boolean isStatic() {
        return java.lang.reflect.Modifier.isStatic(reflectModifiers);
    }

    @Override
    @Used(implemented = true)
    public boolean isFinal() {
        return java.lang.reflect.Modifier.isFinal(reflectModifiers);
    }
}
