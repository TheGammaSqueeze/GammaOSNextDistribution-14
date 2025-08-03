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
import com.sun.javadoc.MemberDoc;
import com.sun.javadoc.PackageDoc;
import com.sun.javadoc.SeeTag;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTree.Kind;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.SeeTree;
import java.util.HashMap;
import java.util.stream.Collectors;
import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;

class SeeTagImpl extends TagImpl implements SeeTag {

    private final SeeTree seeTree;

    protected SeeTagImpl(SeeTree seeTree, Element owner, Context context) {
        super(seeTree, owner, context);

        this.seeTree = seeTree;
    }

    static SeeTagImpl create(SeeTree docTree, Element owner, Context context) {
        var tagsOfElement = context.caches.tags.see.computeIfAbsent(owner,
                el -> new HashMap<>());
        return tagsOfElement.computeIfAbsent(docTree,
                el -> new SeeTagImpl(el, owner, context));
    }

    private String label;

    @Override
    @Unused(implemented = true)
    public String label() {
        if (label == null) {
            var children = seeTree.getReference();
            label = children
                    .stream()
                    .map(DocTree::toString)
                    .collect(Collectors.joining(" "));
        }
        return label;
    }

    private boolean referencedPackageInitialised;
    private PackageDocImpl referencedPackage;

    @Override
    @Unused(implemented = true)
    public PackageDoc referencedPackage() {
        if (!referencedPackageInitialised) {
            String signature = getReferenceSignature(seeTree);
            if (signature != null) {
                PackageElement pe =
                        context.environment.getElementUtils().getPackageElement(signature);
                if (pe != null) {
                    referencedPackage = PackageDocImpl.create(pe, context);
                }
            }
            referencedPackageInitialised = true;
        }
        return referencedPackage;
    }

    @Override
    @Unused(implemented = true)
    public String referencedClassName() {
        var rc = referencedClass();
        return rc.qualifiedName();
    }

    private boolean referencedClassInitialised;
    private ClassDocImpl referencedClass;

    @Override
    @Unused(implemented = true)
    public ClassDocImpl referencedClass() {
        if (!referencedClassInitialised) {
            String signature = getReferenceSignature(seeTree);
            if (signature != null) {
                TypeElement te = context.environment.getElementUtils().getTypeElement(signature);
                if (te != null) {
                    referencedClass = switch (te.getKind()) {
                        case CLASS, ENUM, INTERFACE -> ClassDocImpl.create(te, context);
                        case ANNOTATION_TYPE -> AnnotationTypeDocImpl.create(te, context);
                        default -> throw new UnsupportedOperationException(te.getKind() +
                                " is not yet supported");
                    };
                }
            }
            referencedClassInitialised = true;
        }
        return referencedClass;
    }

    @Override
    @Unused(implemented = true)
    public String referencedMemberName() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused(implemented = true)
    public MemberDoc referencedMember() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    /**
     * Get the signature of a reference to a Java language element (which is in a form of {@link
     * ReferenceTree}) of a given {@link SeeTree}. For cases when reference is anything but {@link
     * ReferenceTree} (e.g. it could be quoted-string or HTML) – return {@code null}.
     *
     * @param seeTree tag
     * @return signature of a reference, or {@code null} if it's not referencing to a Java language
     * element.
     */
    private String getReferenceSignature(SeeTree seeTree) {
        var children = seeTree.getReference();
        if (children.size() >= 1) {
            DocTree ref = children.get(0);
            if (ref.getKind() == Kind.REFERENCE) {
                var asReference = (ReferenceTree) ref;
                return asReference.getSignature();
            }
        }
        return null;
    }
}
