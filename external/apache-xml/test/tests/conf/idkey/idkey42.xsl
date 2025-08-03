<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: idkey42 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.2 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Find all text under IDed node and apply predicate, via match pattern. -->

<xsl:strip-space elements="a b c"/>

<xsl:template match="/">
  <out>
    <xsl:apply-templates select="doc/a//text()"/>
    <xsl:text>&#10;</xsl:text>
  </out>
</xsl:template>

<xsl:template match="id('id2')/*[name()='b']/text()" priority="2">
  <xsl:text>&#10;</xsl:text>
  <bee><xsl:value-of select="../@id"/></bee>
</xsl:template>

<xsl:template match="id('id2')//text()">
  <xsl:text>&#10;</xsl:text>
  <x><xsl:value-of select="../@id"/></x>
</xsl:template>

<xsl:template match="text()">
  <xsl:text>&#10;</xsl:text>
  <other><xsl:value-of select="."/></other>
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
