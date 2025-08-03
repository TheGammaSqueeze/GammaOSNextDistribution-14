<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: Match12 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.4 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test that apply-templates goes down at least 15 levels. -->

<xsl:template match="doc">
  <out>
    <xsl:value-of select="title"/><xsl:text>
</xsl:text>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="title"/><!-- Suppress the default action on these. -->

<xsl:template match="text()"><!-- To suppress empty lines --><xsl:apply-templates/></xsl:template>

<xsl:template match="a">
  <xsl:text>Found an A node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="b">
  <xsl:text>Found a B node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="c">
  <xsl:text>Found a C node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="d">
  <xsl:text>Found a D node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="e">
  <xsl:text>Found an E node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="f">
  <xsl:text>Found an F node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="g">
  <xsl:text>Found a G node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="h">
  <xsl:text>Found an H node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="i">
  <xsl:text>Found an I node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="j">
  <xsl:text>Found a J node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="k">
  <xsl:text>Found a K node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="l">
  <xsl:text>Found an L node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="m">
  <xsl:text>Found an M node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="n">
  <xsl:text>Found an N node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="o">
  <xsl:text>Found an O node
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="p">
  <xsl:text>Found a P node; there should not be one!
</xsl:text>
  <xsl:apply-templates/>
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
