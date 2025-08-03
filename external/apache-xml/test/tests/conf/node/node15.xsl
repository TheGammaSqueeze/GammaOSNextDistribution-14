<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: node15 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.3 -->
  <!-- AdditionalSpec: XSLT 5.2 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test for node() in match patterns. Default axis is child. -->

<xsl:template match="/doc">
  <out>
    <xsl:apply-templates select="*|@*|comment()|processing-instruction()|text()"/>
  </out>
</xsl:template>

<xsl:template match="node()">
  <xsl:text>N-</xsl:text>
  <xsl:choose>
    <xsl:when test="name(.)!=''">
      <xsl:text>named:</xsl:text><xsl:value-of select="name()"/><xsl:text>...</xsl:text>
    </xsl:when>
    <xsl:when test=".!=''">
      <xsl:text>content:</xsl:text><xsl:value-of select="."/>
    </xsl:when>
    <xsl:otherwise>
      <xsl:text>UNKNOWN</xsl:text>
    </xsl:otherwise>
  </xsl:choose>
  <xsl:apply-templates select="@*"/>
  <xsl:apply-templates/>
  <xsl:text>,
</xsl:text>
</xsl:template>

<xsl:template match="attribute::*" priority="-1">
  <xsl:text>A:</xsl:text><xsl:value-of select="name()"/>
  <xsl:text>
  </xsl:text>
</xsl:template>

<xsl:template match="text()" priority="-2"><!-- Override built-in template -->
  <xsl:text>Incorrect text match:</xsl:text><xsl:value-of select="."/>
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
