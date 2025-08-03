<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: namedtemplate03 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 6 -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Test for recursion of xsl:call-template. -->

<!-- <xsl:param name="pvar2" select="'stylesheet-var'"/> -->

<xsl:template match="/doc">
  <out>
    <xsl:variable name="ResultTreeFragTest" select="name(.)"/> 
    <xsl:call-template name="ntmp1">
      <xsl:with-param name="pvar1" select="$ResultTreeFragTest"/>
    </xsl:call-template>
  </out>
</xsl:template>

<xsl:template name="ntmp1">
  <xsl:param name="pvar1">def-text-1</xsl:param>
  <xsl:param name="pvar2">def-text-2</xsl:param>
  <xsl:value-of select="$pvar1"/><xsl:text>,</xsl:text>
  <xsl:value-of select="$pvar2"/><xsl:text>,</xsl:text>
  <xsl:apply-templates select="*">
    <xsl:with-param name="pvar1" select="$pvar1"/>
  </xsl:apply-templates>
</xsl:template>

<xsl:template match="*">
  <xsl:param name="pvar1">def-text-1</xsl:param>
  <xsl:call-template name="ntmp1">
    <xsl:with-param name="pvar1" select="$pvar1"/>
    <xsl:with-param name="pvar2" select="name(.)"/>
  </xsl:call-template>
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
