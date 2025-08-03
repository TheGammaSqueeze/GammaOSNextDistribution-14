<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: extend03 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 14 Extensions -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Test function-available and element-available with
     xslt elements and functions. -->

<xsl:template match="/">
<out><xsl:text>&#010;</xsl:text>
  <xsl:choose>
    <xsl:when test="element-available('xsl:value-of')">
      <xsl:text>element xsl:value-of IS defined</xsl:text>
    </xsl:when>
    <xsl:otherwise>
      <xsl:text>element xsl:value-of IS NOT defined</xsl:text>
    </xsl:otherwise>
  </xsl:choose><xsl:text>&#010;</xsl:text>

  <xsl:choose>
    <xsl:when test="function-available('document')">
      <xsl:text>function document() IS defined</xsl:text>
    </xsl:when>
    <xsl:otherwise>
      <xsl:text>function document() IS NOT defined</xsl:text>
    </xsl:otherwise>
  </xsl:choose><xsl:text>&#010;</xsl:text>

</out>
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
