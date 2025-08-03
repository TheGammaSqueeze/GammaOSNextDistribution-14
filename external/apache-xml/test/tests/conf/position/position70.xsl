<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: position70 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.4 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test that set of nodes changes when strip-space is in effect. -->
  <!-- "The xsl:apply-templates instruction processes all children of the current node,
        including text nodes. However, text nodes that have been stripped as specified
        in 3.4 Whitespace Stripping will not be processed." -->

<xsl:strip-space elements="doc inner inner2"/>

<xsl:template match="/doc">
  <out>
    <xsl:apply-templates select="*|@*|comment()|text()"/>
  </out>
</xsl:template>

<xsl:template match="*"><!-- for child elements -->
  <xsl:text>E(</xsl:text>
  <xsl:value-of select="position()"/>
  <xsl:text>):</xsl:text>
  <xsl:value-of select="name()"/>
  <xsl:apply-templates select="@*"/>
  <xsl:apply-templates/>
  <xsl:text>
</xsl:text>
</xsl:template>

<xsl:template match="@*">
  <!-- The parser has freedom to present attributes in any order it wants.
     Input file should have only one attribute if you want consistent results across parsers. -->
  <xsl:text>A(</xsl:text>
  <xsl:value-of select="position()"/>
  <xsl:text>):</xsl:text>
  <xsl:value-of select="name()"/>
</xsl:template>

<xsl:template match="text()">
  <xsl:text>T(</xsl:text>
  <xsl:value-of select="position()"/>
  <xsl:text>):</xsl:text>
  <xsl:value-of select="."/>
</xsl:template>

<xsl:template match="comment()">
  <xsl:text>C(</xsl:text>
  <xsl:value-of select="position()"/>
  <xsl:text>):</xsl:text>
  <xsl:value-of select="."/>
</xsl:template>


  <!--
   * Licensed to the Apache Software Foundation (ASF) under one
   * or more contributor license agreements. See the NOTICE file
   * distributed with this work for additional information
   * regarding copyright ownership. The ASF licenses this file
   * to you under the Apache License, Version 2.0 (the  "License");
   * you may not use this file except in compliance with the License.
   * You may obtain a copy of the License at
   *
   *     http://www.apache.org/licenses/LICENSE-2.0
   *
   * Unless required by applicable law or agreed to in writing, software
   * distributed under the License is distributed on an "AS IS" BASIS,
   * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   * See the License for the specific language governing permissions and
   * limitations under the License.
  -->

</xsl:stylesheet>
