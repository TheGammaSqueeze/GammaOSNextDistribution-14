<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variable40 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.2 Values of Variables and Parameters  -->
  <!-- Purpose: Test top-level xsl:variable set using apply-templates -->
  <!-- Author: David Marston -->

<xsl:variable name="TreeFrag">
  <xsl:apply-templates select="//b" />
</xsl:variable>

<xsl:template match="doc">
  <out>
    <xsl:value-of select="$TreeFrag"/><xsl:text>,</xsl:text>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="a|d">
  <xsl:value-of select="name(.)"/><xsl:text>,</xsl:text>
  <xsl:apply-templates/>
</xsl:template>

<xsl:template match="b">
  <xsl:apply-templates/><!-- expect only text, not child elements -->
</xsl:template>

<xsl:template match="c">
  <xsl:text>-</xsl:text><xsl:value-of select="."/><xsl:text>;</xsl:text>
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
