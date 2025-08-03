<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: MODES10 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.7 Modes -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Show that we only go into a mode via apply-templates.
     You can't put a mode on call-template, and the fact that you call a named
     template that has a mode specifier doesn't mean you are in that mode. -->

<xsl:template match="/">
  <out>
    <xsl:apply-templates select="doc" mode="a"/>
  </out>
</xsl:template>

<xsl:template match="doc" mode="a" priority="3">
  <xsl:text>Found doc...</xsl:text>
  <xsl:call-template name="scan"/>
</xsl:template>

<!-- The following template is both applied in mode a and called -->
<xsl:template name="scan" match="*" mode="a" priority="2">
  <xsl:text>Scanned </xsl:text><xsl:value-of select="name(.)"/><xsl:text>
</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="x" priority="4">
  <xsl:text>Found x, no mode: </xsl:text><xsl:value-of select="."/><xsl:text>
</xsl:text>
  <xsl:apply-templates mode="a"/>
</xsl:template>

<xsl:template match="x" mode="a" priority="4">
  <xsl:text>Found x, mode a: </xsl:text><xsl:value-of select="@test"/><xsl:text>
</xsl:text>
</xsl:template>

<xsl:template match="text()">
  <xsl:text>modeless text: </xsl:text><xsl:value-of select="."/><xsl:text>
</xsl:text>
</xsl:template>

<xsl:template match="text()" mode="a">
  <xsl:text>mode a text: </xsl:text><xsl:value-of select="."/>
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
