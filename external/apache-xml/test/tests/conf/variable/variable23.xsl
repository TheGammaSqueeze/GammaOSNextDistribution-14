<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variable23 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 -->
  <!-- Purpose: Test how big a string can be passed to a template. -->
  <!-- Author: David Marston -->

<!-- Set upper limit here -->
<xsl:variable name="max" select="256" />

<xsl:template match="/doc">
  <out>
    <xsl:call-template name="looper">
      <xsl:with-param name="str" select="'....5....|'" />
    </xsl:call-template>
  </out>
</xsl:template>

<xsl:template name="looper">
  <xsl:param name="str" select="0000000000" />
  <!-- put out one iteration of the name and a trailing space -->
  <xsl:value-of select="string-length($str)"/><xsl:text>: </xsl:text>
  <xsl:value-of select="$str"/><xsl:text>
</xsl:text>
  <!-- here's the recursion -->
  <xsl:if test="string-length($str) &lt; $max">
    <xsl:call-template name="looper">
      <xsl:with-param name="str">
        <xsl:value-of select="concat($str,'....5....|')"/>
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
