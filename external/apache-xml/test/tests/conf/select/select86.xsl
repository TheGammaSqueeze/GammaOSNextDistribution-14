<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<!-- FileName: select86 -->
<!-- Document: http://www.w3.org/TR/xslt -->
<!-- DocVersion: 19991116 -->
<!-- Section: 12.4 Miscellaneous Additional Functions -->
<!-- Purpose: Test that current() returns a node-set suitable for count(). -->
<!-- Creator: David Marston -->
<!-- Elaboration: There was a bug, masked by current()/sub-node -->

<xsl:output method="xml" indent="no" encoding="UTF-8"/>

<xsl:template match="doc">
  <out>
    <xsl:text>&#10;</xsl:text>
    <xsl:apply-templates select="m"/>
    <xsl:text>&#10;</xsl:text>
    <xsl:for-each select="m">
      <for1><xsl:value-of select="count(current())"/></for1>
      <xsl:text>&#10;</xsl:text>
      <for2><xsl:value-of select="following-sibling::*[count(current())]"/></for2>
    </xsl:for-each>
    <xsl:text>&#10;</xsl:text>
  </out>
</xsl:template>

<xsl:template match="m">
  <apply><xsl:value-of select="count(current())"/></apply>
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
