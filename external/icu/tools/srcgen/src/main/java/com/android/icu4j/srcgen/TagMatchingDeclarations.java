/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.icu4j.srcgen;

import com.google.common.collect.Lists;
import com.google.currysrc.api.process.Context;
import com.google.currysrc.api.process.JavadocUtils;
import com.google.currysrc.api.process.Processor;
import com.google.currysrc.api.process.ast.BodyDeclarationLocator;
import com.google.currysrc.api.process.ast.StartPositionComparator;

import org.eclipse.jdt.core.dom.BodyDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Adds a javadoc tag to {@link BodyDeclaration}s that match a list of locators.
 */
public class TagMatchingDeclarations implements Processor {
  private final List<LocatorTag> locatorTags;

  /**
   * @param tagComment The same tag applied to all locators
   */
  public TagMatchingDeclarations(List<BodyDeclarationLocator> locatorList, String tagComment) {
    locatorTags = locatorList.stream()
            .map((locator) -> new LocatorTag(locator, tagComment))
            .collect(Collectors.toList());
  }

  public TagMatchingDeclarations(Map<BodyDeclarationLocator, String> locatorTagMap) {
    locatorTags = locatorTagMap.entrySet().stream()
            .map((entry) -> new LocatorTag(entry.getKey(), entry.getValue()))
            .collect(Collectors.toList());
  }

  @Override public void process(Context context, CompilationUnit cu) {
    List<DeclarationTag> matchingNodes = Lists.newArrayList();
    // This is inefficient but it is very simple.
    for (LocatorTag locatorTag : locatorTags) {
      BodyDeclaration bodyDeclaration = locatorTag.locator.find(cu);
      if (bodyDeclaration != null) {
        matchingNodes.add(new DeclarationTag(bodyDeclaration, locatorTag.tagComment));
      }
    }
    // Tackle nodes in reverse order to avoid messing up the ASTNode offsets.
    Collections.sort(matchingNodes, Comparator.comparingInt(o -> o.declaration.getStartPosition()));
    ASTRewrite rewrite = context.rewrite();
    for (DeclarationTag declarationTag : Lists.reverse(matchingNodes)) {
      JavadocUtils.addJavadocTag(rewrite, declarationTag.declaration, declarationTag.tagComment);
    }
  }

  @Override public String toString() {
    return "TagDeclarations{" +
        "locatorTags=" + locatorTags +
        '}';
  }

  private static class LocatorTag {
    final BodyDeclarationLocator locator;
    final String tagComment;

    LocatorTag(BodyDeclarationLocator locator, String tagComment) {
      this.locator = locator;
      this.tagComment = tagComment;
    }

    @Override
    public String toString() {
      return "LocatorTag{" +
              "locator=" + locator +
              ", tagComment='" + tagComment + '\'' +
              '}';
    }
  }

  private static class DeclarationTag {
    final BodyDeclaration declaration;
    final String tagComment;

    DeclarationTag(BodyDeclaration declaration, String tagComment) {
      this.declaration = declaration;
      this.tagComment = tagComment;
    }
  }
}
