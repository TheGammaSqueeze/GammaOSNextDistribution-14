<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: position78 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 -->
  <!-- Purpose: Look at preceding axis (reverse document order) filtered by node test. -->
  <!-- Author: David Marston -->

<xsl:template match="/">
  <out>
    <xsl:apply-templates select=".//center" />
  </out>
</xsl:template>

<xsl:template match="center">
  <xsl:variable name="num" select="count(preceding::text())" />
  <xsl:text>There are </xsl:text>
  <xsl:value-of select="$num"/>
  <xsl:text> preceding text nodes
</xsl:text>
  <xsl:call-template name="display-loop">
    <xsl:with-param name="this" select="1"/>
    <xsl:with-param name="total" select="$num"/>
  </xsl:call-template>
</xsl:template>

<xsl:template name="display-loop">
  <xsl:param name="this"/>
  <xsl:param name="total"/>
  <xsl:text>Position </xsl:text>
  <xsl:value-of select="$this"/>
  <xsl:text> is </xsl:text>
  <xsl:value-of select="preceding::text()[$this]"/>
  <xsl:if test="$this &lt; $total">
    <xsl:call-template name="display-loop">
      <xsl:with-param name="this" select="$this + 1"/>
      <xsl:with-param name="total" select="$total"/>
    </xsl:call-template>
  </xsl:if>
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
