<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: idkey38 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.2 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test id() filtered by a predicate, in a match pattern. -->

<xsl:template match="/">
  <out>
    <xsl:for-each select="tee/s">
      <xsl:apply-templates select="r"/>
    </xsl:for-each>
  </out>
</xsl:template>

<xsl:template match="id('d')/*[@size &gt; 17]" priority="2">
  <xsl:text>&#10;</xsl:text>
  <bigD><xsl:value-of select="../@id"/></bigD>
</xsl:template>

<xsl:template match="r[@size &gt; 17]">
  <xsl:text>&#10;</xsl:text>
  <big><xsl:value-of select="../@id"/></big>
</xsl:template>

<xsl:template match="r">
  <xsl:text>&#10;</xsl:text>
  <other><xsl:value-of select="../@id"/></other>
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
