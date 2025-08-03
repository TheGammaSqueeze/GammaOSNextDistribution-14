<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: ParameterTest.xsl -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19990922 -->
  <!-- Section: 11.2 Values of Variables and Parameters  -->
  <!-- Purpose: Functional test of various usages of parameters in transforms -->

<!-- Declare one of each flavor of common global param statements -->
<xsl:param name="p1">
    <B>ABC</B>
</xsl:param>

<xsl:param name="p2"><B>DEF</B></xsl:param>

<xsl:param name="t1">notset</xsl:param>

<xsl:param name="s1" select="'s1val'">
</xsl:param>

<xsl:param name="s2" select="'s2val'"></xsl:param>

<xsl:template match="doc">
  <xsl:param name="p3"><B>GHI</B></xsl:param>
  <xsl:param name="s3" select="'s3val'"></xsl:param>

  <outp>
    <xsl:value-of select="$p1"/><xsl:text>,</xsl:text><xsl:copy-of select="$p1"/><xsl:text>; </xsl:text>
    <xsl:value-of select="$p2"/><xsl:text>,</xsl:text><xsl:copy-of select="$p2"/><xsl:text>; </xsl:text>
    <xsl:value-of select="$p3"/><xsl:text>,</xsl:text><xsl:copy-of select="$p3"/><xsl:text>; </xsl:text>
  </outp>
  <xsl:text>:
  </xsl:text>
  <outs>
    <xsl:value-of select="$s1"/><xsl:text>,</xsl:text><xsl:copy-of select="$s1"/><xsl:text>; </xsl:text>
    <xsl:value-of select="$s2"/><xsl:text>,</xsl:text><xsl:copy-of select="$s2"/><xsl:text>; </xsl:text>
    <xsl:value-of select="$s3"/><xsl:text>,</xsl:text><xsl:copy-of select="$s3"/><xsl:text>; </xsl:text>
  </outs>
  <xsl:text>:
  </xsl:text>

  <outt>
    <xsl:value-of select="$t1='notset'"/><xsl:text>-notset,</xsl:text>
    <xsl:value-of select="$t1=''"/><xsl:text>-blank,</xsl:text>
    <xsl:value-of select="$t1='a'"/><xsl:text>-a,</xsl:text>
    <xsl:value-of select="$t1='1'"/><xsl:text>-1,</xsl:text>
    <xsl:value-of select="$t1"/>
  </outt>
  <xsl:apply-templates/>

  <!-- @todo Should also call a template and pass a param as well -->
</xsl:template>

<xsl:template match="item[text() = '2']">
  <xsl:text>Item-2-found</xsl:text>
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
