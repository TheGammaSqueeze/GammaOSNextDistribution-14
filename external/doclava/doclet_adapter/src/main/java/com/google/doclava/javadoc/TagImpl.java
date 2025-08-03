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
import com.sun.javadoc.Doc;
import com.sun.javadoc.SourcePosition;
import com.sun.javadoc.Tag;
import com.sun.source.doctree.BlockTagTree;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.DocTree.Kind;
import com.sun.source.doctree.InlineTagTree;
import com.sun.source.doctree.ParamTree;
import com.sun.source.doctree.SeeTree;
import com.sun.source.doctree.SerialFieldTree;
import com.sun.source.doctree.ThrowsTree;
import com.sun.source.util.SimpleDocTreeVisitor;
import java.util.HashMap;
import java.util.Objects;
import javax.lang.model.element.Element;

class TagImpl implements Tag {

    protected final DocTree docTree;
    protected final Element owner;
    protected final Context context;

    protected TagImpl(DocTree docTree, Element owner, Context context) {
        this.docTree = docTree;
        this.owner = owner;
        this.context = context;
    }

    static TagImpl create(DocTree docTree, Element owner, Context context) {
        return switch (docTree.getKind()) {
            case SEE -> SeeTagImpl.create((SeeTree) docTree, owner, context);
            case PARAM -> ParamTagImpl.create((ParamTree) docTree, owner, context);
            case SERIAL_FIELD -> SerialFieldTagImpl.create((SerialFieldTree) docTree,
                    owner, context);
            case THROWS -> ThrowsTagImpl.create((ThrowsTree) docTree, owner, context);
            default -> {
                var tagsOfElement = context.caches.tags.generic.computeIfAbsent(owner,
                        el -> new HashMap<>());
                yield tagsOfElement.computeIfAbsent(docTree, el -> new TagImpl(el, owner,
                        context));
            }
        };
    }

    private String name;

    @Override
    @Used(implemented = true)
    public String name() {
        if (name == null) {
            name = "@" + NAME_VISITOR.visit(docTree, context);
        }
        return name;
    }

    @Override
    @Unused(implemented = true)
    public Doc holder() {
        return context.obtain(owner);
    }

    private String kind;

    @Override
    @Used(implemented = true)
    public String kind() {
        if (kind == null) {
            kind = "@" + remapKind(NAME_VISITOR.visit(docTree, context));
        }
        return kind;
    }

    static String remapKind(String name) {
        Objects.requireNonNull(name);
        return switch (name) {
            case "exception" -> "throws";
            case "link", "linkplain" -> "see";
            case "serialData" -> "serial";
            default -> name;
        };
    }

    private String text;

    @Override
    @Used(implemented = true)
    public String text() {
        if (text == null) {
            text = docTree.toString();
        }
        return text;
    }

    @Override
    @Unused
    public Tag[] inlineTags() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Unused
    public Tag[] firstSentenceTags() {
        throw new UnsupportedOperationException("not yet implemented");
    }

    @Override
    @Used(implemented = true)
    public SourcePosition position() {
        return SourcePositionImpl.create(context, owner, docTree);
    }

    private static final SimpleDocTreeVisitor<String, Context> NAME_VISITOR =
            new SimpleDocTreeVisitor<>() {
                @Override
                protected String defaultAction(DocTree node, Context context) {
                    if (node instanceof BlockTagTree bt) {
                        return bt.getTagName();
                    }
                    if (node instanceof InlineTagTree it) {
                        return it.getTagName();
                    }
                    if (node.getKind() == Kind.ERRONEOUS) {
                        System.err.println("Malformed tag: " + node.toString());
                        return "erroneous";
                    }
                    return node.getKind().toString().toLowerCase();
                }
            };
}
