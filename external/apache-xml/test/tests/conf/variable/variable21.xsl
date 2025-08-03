<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variable21 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.2 Values of Variables and Parameters -->
  <!-- Purpose: Test setting several parameters locally -->
  <!-- Author: David Marston -->

<xsl:template match="doc">
  <xsl:param name="n1" select="1"/>
  <xsl:param name="n2" select="2"/>
  <xsl:param name="n3" select="3"/>
  <xsl:param name="n4" select="4"/>
  <xsl:param name="n5" select="5"/>
  <xsl:param name="n6" select="6"/>
  <xsl:param name="n7" select="7"/>
  <xsl:param name="n8" select="8"/>
  <xsl:param name="n9" select="9"/>
  <out>
    <xsl:value-of select="item[$n8]"/>
    <xsl:value-of select="item[$n3]"/>
    <xsl:value-of select="item[$n7]"/>
    <xsl:value-of select="item[$n4]"/>
    <xsl:value-of select="item[$n1]"/>
    <xsl:value-of select="item[$n6]"/>
    <xsl:value-of select="item[$n5]"/>
    <xsl:value-of select="item[$n9]"/>
    <xsl:value-of select="item[$n2]"/>
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
