<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variableerr20 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 -->
  <!-- Purpose: Try to set same param twice inside a template, after setting via with-param. -->
  <!-- ExpectedException: Variable is already declared in this template -->
  <!-- Author: David Marston -->

<xsl:template match="doc">
  <out>
    <xsl:call-template name="secondary">
      <xsl:with-param name="a" select="'zero'"/><!-- Two sets of quotes make it a string -->
    </xsl:call-template>
  </out>
</xsl:template>

<xsl:template name="secondary">
  <xsl:param name="a" select="'first'" />
  <xsl:param name="a" select="'second'" />
  <xsl:value-of select="$a" />
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
