<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: namedtemplate10 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 6 Named Templates -->
  <!-- Purpose: Test of simulated numerically-indexed for loop. -->
  <!-- This is example 77 from Nic Miloslav's tutorial site. -->

<xsl:template match="/">
  <out>
  	<xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="/doc/*">
  <p>
  <xsl:call-template name="for">
    <xsl:with-param name="stop">
      <xsl:value-of select="@repeat"/>
    </xsl:with-param>
  </xsl:call-template>
  </p>
</xsl:template>

<xsl:template name="for">
  <xsl:param name="start">1</xsl:param>
  <xsl:param name="stop">1</xsl:param>
  <xsl:param name="step">1</xsl:param>
  <!-- put out one iteration of the name and a trailing space -->
  <xsl:value-of select="name()"/>
  <xsl:text> </xsl:text>
  <!-- here's the recursion -->
  <xsl:if test="$start &lt; $stop">
    <xsl:call-template name="for">
      <xsl:with-param name="stop">
        <xsl:value-of select="$stop"/>
      </xsl:with-param>
      <xsl:with-param name="start">
        <xsl:value-of select="$start + $step"/>
      </xsl:with-param>
    </xsl:call-template>
  </xsl:if>
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
