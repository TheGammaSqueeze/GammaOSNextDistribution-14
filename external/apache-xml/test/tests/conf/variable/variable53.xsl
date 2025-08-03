<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">

  <!-- FileName: variable53 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 -->
  <!-- Author: John Howard -->
  <!-- Purpose: test using recursion to traverse a node-set in a variable. -->
 
<xsl:output method="xml"/>

<xsl:template match="/">
  <out>
    <xsl:call-template name="traverse-vals">
      <xsl:with-param name="pos" select="1"/>
    </xsl:call-template>
  </out>
</xsl:template>

<xsl:template name="traverse-vals">
  <xsl:param name="pos"/>
  <xsl:variable name="series" select="/data/*/datum/@value"/>

  <xsl:value-of select="concat($series[number($pos)],' ')"/>
  <xsl:if test="$pos &lt; count($series)">
    <xsl:call-template name="traverse-vals">
      <xsl:with-param name="pos" select="$pos + 1"/>
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
