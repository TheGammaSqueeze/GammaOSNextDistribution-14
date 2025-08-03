<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: node07 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3 Data Model -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Processing Instructions and comments within a stylesheet 
       are ignored. -->

<?MyPI version="1.0"?>

<xsl:template match="doc">
  <out>
  <?MyPI version="2.0"?>
  <!-- This template prints out 'Testing 1 2 3' -->
    <xsl:text>&#010;</xsl:text>
    <xsl:value-of select="'Testing '"/>
    <xsl:for-each select="*">
      <xsl:value-of select="."/><xsl:text> </xsl:text>		
      <?MyPI version="3.0"?>
    </xsl:for-each>

  <!-- This calls a named template -->
    <xsl:call-template name="ThatTemp">
      <xsl:with-param name="sam">quos</xsl:with-param>
      <?MyPI version="4.0"?>
    </xsl:call-template>
    <xsl:text>&#010;</xsl:text>
  </out>
</xsl:template>

<!-- This is the named template that is called from above -->
<xsl:template name="ThatTemp">
  <xsl:param name="sam">bo</xsl:param>
  <?MyPI version="5.0"?>
  <xsl:copy-of select="$sam"/>
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
