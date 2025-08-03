<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: modes15 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.7 Modes -->
  <!-- Purpose: Re-use nodes in different modes; templates have step-paths -->
  <!-- Creator: Mingfei Peng (mfpeng@excite.com), altered by David Marston -->
  <!-- Within a given mode, there are situations when more than one template
    will match. Normal conflict-resolution rules should apply. -->

<xsl:template match="/">
  <out>
    <xsl:apply-templates select="sss/sss" mode="c"/>
    <xsl:apply-templates select="sss/sss" mode="d"/>
    <xsl:apply-templates select="sss//i" mode="c"/>
    <xsl:apply-templates select="sss//i" mode="d"/>
    <xsl:apply-templates select="/sss/sss/i" mode="c"/>
    <xsl:apply-templates select="/sss/sss/i" mode="d"/>
  </out>
</xsl:template>

<xsl:template match="sss//*" mode="d">
 !Any descendant of any sss!
</xsl:template>

<xsl:template match="/sss//*" mode="d">
 +Any descendant of root sss+
</xsl:template>

<xsl:template match="sss/*" mode="c">
 -Any child of any sss-
</xsl:template>

<xsl:template match="/sss/*" mode="c">
 -Any child of root sss-
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
