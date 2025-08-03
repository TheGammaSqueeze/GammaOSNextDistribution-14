<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: select75 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.1 Root node -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Ensure that select='/' gets what it should. -->

<xsl:template match="/">
  <out>
    <xsl:apply-templates select="doc"/>
    <xsl:text>
</xsl:text>
  </out>
</xsl:template>

<xsl:template match="doc">
  <xsl:apply-templates select="comment()" mode="showcomments"/>
  <xsl:apply-templates select="inner"/>
</xsl:template>

<xsl:template match="inner">
  <xsl:apply-templates select="/" mode="showcomments"/>
</xsl:template>

<xsl:template match="/" mode="showcomments">
  <xsl:text>
...Back to top...</xsl:text>
  <xsl:apply-templates select="comment()" mode="showcomments"/>
</xsl:template>

<xsl:template match="comment()" mode="showcomments">
  <xsl:text>
Comment found:</xsl:text>
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
