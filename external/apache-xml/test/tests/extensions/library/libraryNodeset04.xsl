<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ex="http://xml.apache.org/xalan"
                extension-element-prefixes="ex">

  <!-- FileName: libraryNodeset04 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.4 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Ensure that traversal of nodeset of global RTF gets the right one. -->

<xsl:output method="xml" indent="no" encoding="UTF-8"/>

<xsl:variable name="top1">
  <t0>top1-begin
    <t1>top1-first1</t1>
    <t2>top1-first2</t2>
    <t1>top1-second1</t1>
  </t0>
</xsl:variable>

<xsl:variable name="top2">
  <t0>top2-begin
    <t1>top2-first1</t1>
    <t2>top2-first2</t2>
    <t1>top2-second1</t1>
  </t0>
</xsl:variable>

<xsl:template match="doc">
  <out>
    <!-- First, force evaluation of each variable -->
    <junk>
      <xsl:text>$top1 summary: </xsl:text>
      <xsl:value-of select="$top1"/>
      <xsl:text>
</xsl:text>
      <xsl:text>$top2 summary: </xsl:text>
      <xsl:value-of select="$top2"/>
    </junk>
    <xsl:text>
</xsl:text>
    <xsl:text>The preceding::t1 elements in $top2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($top2)//t2/preceding::t1">
      <xsl:value-of select="."/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
  </out>
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
