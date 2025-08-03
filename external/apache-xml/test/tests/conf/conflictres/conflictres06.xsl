<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: conflictres06 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.5 -->
  <!-- Purpose: Test for conflict resolution on a predicate -->
  <!-- Creator: Paul Dick -->
  <!-- No conflict warnings should be seen. -->

<xsl:template match="doc">
  <out>
    <xsl:apply-templates select="file"/>
  </out>
</xsl:template>

<xsl:template match="file[@test='false']"><!-- default priority is 0.5 -->
  <xsl:text>Match-predicated-node-name</xsl:text>
</xsl:template>

<xsl:template match="file"><!-- default priority is 0 -->
  <xsl:text>Match-on-node-name,</xsl:text>
</xsl:template>

<xsl:template match="*">
  <xsl:text>Match-of-wildcard,</xsl:text>
</xsl:template>

<xsl:template match="node()">
  <xsl:text>Match-of-node,</xsl:text>
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
