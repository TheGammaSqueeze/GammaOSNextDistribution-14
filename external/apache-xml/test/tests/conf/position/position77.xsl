<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: position77 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.4 -->
  <!-- AdditionalSpec: 4, 10 and "current node list" in 1 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test positional indexing in current node list passed
       via apply-templates with select that has predicate. -->

<xsl:template match="metadata">
  <out>
    <xsl:apply-templates select="CLASSIFICATION"/>
    <xsl:text>&#10;====== tickers ======&#10;</xsl:text>
    <xsl:apply-templates select="keyword[@tag='ticker']"/>
    <xsl:text>&#10;====== sectors ======&#10;</xsl:text>
    <xsl:apply-templates select="keyword[@tag='sector']"/>
  </out>
</xsl:template>

<xsl:template match="keyword">
  <xsl:value-of select="@value"/>
  <xsl:value-of select="position()"/>
  <xsl:value-of select="last()"/>
  <xsl:if test="position()!=last()">,</xsl:if>
</xsl:template>

<xsl:template match="*">
  <xsl:value-of select="@value"/>
  <xsl:value-of select="position()"/>
  <xsl:value-of select="last()"/>
  <xsl:if test="position()!=last()">,</xsl:if>
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
