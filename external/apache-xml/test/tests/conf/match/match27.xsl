<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">


  <!-- FileName: match27 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.2 -->
  <!-- Creator: David Marston, from an idea by Holger Floerke -->
  <!-- Purpose: // at start of match pattern should not affect selection of nodes. -->

<xsl:output method="xml" encoding="UTF-8" />

<xsl:template match="/">
  <out>
    <xsl:text>&#10;</xsl:text>
    <xsl:apply-templates select="//X" mode="unprefixed" />
    <xsl:text>&#10;</xsl:text>
    <xsl:apply-templates select="//X" mode="prefixed" />
  </out>
</xsl:template>

<xsl:template match="X" mode="unprefixed">
  <foundX><xsl:copy-of select="@level"/></foundX>
</xsl:template>

<xsl:template match="//X" mode="prefixed">
  <found-X><xsl:copy-of select="@level"/></found-X>
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
