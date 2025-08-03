<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- FileName: impincl21 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 5.6 -->
  <!-- Creator: Morten Jorgensen -->
  <!-- Purpose: Show selection of templates from files with 1st and 2nd import precedence. -->

<xsl:import href="fragments/imp21b.xsl"/>
<xsl:import href="fragments/imp21d.xsl"/>

<xsl:template match="/doc">
  <out>
    <xsl:text>Match on /doc in top xsl&#xa;</xsl:text>
    <xsl:apply-templates select="*"/>
  </out>
</xsl:template>

<xsl:template match="foo">
  <A><xsl:apply-imports/></A><xsl:text>&#xa;</xsl:text>
</xsl:template>

<xsl:template match="goo">
  <A><xsl:apply-imports/></A><xsl:text>&#xa;</xsl:text>
</xsl:template>

<xsl:template match="bar">
  <xsl:text> - match on bar in top xsl</xsl:text>
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
