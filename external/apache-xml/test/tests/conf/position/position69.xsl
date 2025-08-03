<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: position69 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.4 -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Test positional indexing with for-each loop and apply-templates. -->

<xsl:template match="metadata">
  <out>
    <xsl:text>&#010;</xsl:text>
    <xsl:for-each select="keyword[@tag='ticker']">
      <xsl:sort select="@value"/>
      <xsl:value-of select="@value"/>
      <xsl:value-of select="position()"/>
      <xsl:value-of select="last()"/>
      <xsl:if test="position()!=last()">,</xsl:if>
    </xsl:for-each>

    <xsl:text>&#010;</xsl:text>

    <xsl:apply-templates select="keyword[@tag='ticker']">
      <xsl:sort select="@value"/>
    </xsl:apply-templates>
    <xsl:text>&#010;</xsl:text>
  </out>
</xsl:template>

<xsl:template match="keyword">
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
