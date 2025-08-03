<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: idkey08 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.2 Keys -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test for xsl:key, where value of use is an integer. -->

<xsl:key name="mykey" match="div" use="number(q)"/>

<xsl:template match="doc">
  <out>
    <xsl:value-of select="key('mykey', 1 )/p"/><xsl:text>,</xsl:text>
    <xsl:value-of select="key('mykey', 3.0 )/p"/><xsl:text>,</xsl:text>
    <xsl:value-of select="key('mykey', 1+1 )/p"/><xsl:text>,</xsl:text>
    <xsl:value-of select="key('mykey', 3.7 )/p"/>
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
