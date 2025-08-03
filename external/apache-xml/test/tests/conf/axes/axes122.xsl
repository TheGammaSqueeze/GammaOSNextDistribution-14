<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: AXES122 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test for last() on two-step paths, contrasting descendant-or-self with ancestor-or-self. -->

<xsl:output method="xml" encoding="UTF-8"/>

<xsl:template match="/">
  <out>
    <xsl:text>From root: </xsl:text>
    <xsl:value-of select="count(descendant-or-self::*/@att1)"/><xsl:text>&#10;</xsl:text>
    <xsl:apply-templates select="doc"/>
  </out>
</xsl:template>

<xsl:template match="doc">
  <xsl:text>From doc: </xsl:text>
  <xsl:value-of select="count(descendant-or-self::*/@att1)"/><xsl:text>= </xsl:text>
  <xsl:value-of select="descendant-or-self::*/@att1[last()]"/><xsl:text>, </xsl:text>
  <xsl:value-of select="descendant-or-self::*[last()]/@att1"/><xsl:text>, </xsl:text>
  <xsl:value-of select="(descendant-or-self::*/@att1)[last()]"/><xsl:text>; &#10;</xsl:text>
  <!-- Now reposition on the lowest node and look upward. -->
  <xsl:for-each select="//baz">
    <xsl:text>From baz: </xsl:text>
    <xsl:value-of select="count(ancestor-or-self::*/@att1)"/><xsl:text>= </xsl:text>
      <xsl:value-of select="ancestor-or-self::*/@att1[last()]"/><xsl:text>, </xsl:text>
      <xsl:value-of select="(ancestor-or-self::*)/@att1[last()]"/><xsl:text>, </xsl:text>
      <xsl:value-of select="(ancestor-or-self::*/@att1)[last()]"/><xsl:text>, </xsl:text>
      <xsl:value-of select="(ancestor::*|self::*)/@att1[last()]"/><xsl:text>, </xsl:text>
      <xsl:value-of select="((ancestor::*|self::*)/@att1)[last()]"/><xsl:text>; &#10;</xsl:text>
  </xsl:for-each>
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
