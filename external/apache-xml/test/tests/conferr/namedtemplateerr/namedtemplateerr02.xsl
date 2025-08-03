<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: namedtemplateerr02 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 -->
  <!-- Purpose: Error test- parameter of outer template unknown inside inner template. -->
  <!-- Creator: David Marston -->
  <!-- ExpectedException: Could not find variable with the name of pvar1 -->

<xsl:template match="doc">
  <out>
    <xsl:call-template name="tmplt1">
      <xsl:with-param name="pvar1" select="555"/>
    </xsl:call-template>
<xsl:text>
Back to main template.</xsl:text>
  </out>
</xsl:template>

<xsl:template name="tmplt1">
  <xsl:param name="pvar1">pvar1 default data</xsl:param>
  <!-- pvar1 won't be in scope in next template, so pass it in via new variable. -->
  <xsl:variable name="passto2">
    <xsl:value-of select="number($pvar1)"/>
  </xsl:variable>

  <xsl:value-of select="$passto2"/><xsl:text>, </xsl:text>
  <xsl:call-template name="tmplt2">
    <xsl:with-param name="t1num" select="$passto2"/>
  </xsl:call-template>
  <xsl:text>
Back to template 1.</xsl:text>
</xsl:template>

<xsl:template name="tmplt2">
  <xsl:param name="t1num">t1num default data</xsl:param>
  <xsl:value-of select="444 + $t1num"/><xsl:text>, prior item should be 999, </xsl:text>
  <xsl:value-of select="222 + $pvar1"/><xsl:text>, prior item should be 777</xsl:text>
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
