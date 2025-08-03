<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variable20 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11 -->
  <!-- Purpose: Verify that a variable in a for-each can take on a new value -->
  <!-- It's unclear whether the spec allows this. XT does; Saxon doesn't. -->
  <!-- Author: David Marston -->

<xsl:template match="/">
  <out>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="doc">
  <xsl:variable name="var" select="'level1'"/>
  <xsl:text>begin doc template, $var=</xsl:text><xsl:value-of select="$var"/><xsl:text>
</xsl:text>
  <xsl:for-each select="a">
    <xsl:variable name="var" select="@pos"/>
    <xsl:text>inside for-each, $var=</xsl:text><xsl:value-of select="$var"/><xsl:text>
</xsl:text>
  </xsl:for-each>
  <xsl:text>end doc template, $var=</xsl:text><xsl:value-of select="$var"/><xsl:text>
</xsl:text>
</xsl:template>

<xsl:template match="text()"/><!-- suppress empty lines -->


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
