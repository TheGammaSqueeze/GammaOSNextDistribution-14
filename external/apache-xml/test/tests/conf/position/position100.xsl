<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: position100 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test count() starting on a text node and going upward. -->

<xsl:output method="xml" indent="no" encoding="UTF-8"/>

<xsl:template match="/">
  <out>
    <xsl:for-each select="//center/text()[1]">
      <xsl:value-of select="count(ancestor-or-self::node())"/><xsl:text> nodes on this axis:
</xsl:text>
      <xsl:apply-templates select="ancestor-or-self::node()" mode="census"/>
    </xsl:for-each>
  </out>
</xsl:template>

<xsl:template match="/" mode="census">
  <xsl:text>Root Node
</xsl:text>
</xsl:template>

<xsl:template match="*" mode="census">
  <xsl:text>E: </xsl:text><xsl:value-of select="name(.)"/><xsl:text>
</xsl:text>
</xsl:template>

<xsl:template match="@*" mode="census">
  <xsl:text>A: </xsl:text><xsl:value-of select="name(.)"/><xsl:text>
</xsl:text>
</xsl:template>

<xsl:template match="text()" mode="census">
  <xsl:text>T: </xsl:text><xsl:value-of select="."/><xsl:text>
</xsl:text>
</xsl:template>

<xsl:template match="comment()|processing-instruction()" mode="census">
  <xsl:text>ERROR! </xsl:text><xsl:value-of select="."/><xsl:text>
</xsl:text>
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
