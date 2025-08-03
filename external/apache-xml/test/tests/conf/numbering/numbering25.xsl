<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- CaseName: numbering25 -->
  <!-- Author: David Marston -->
  <!-- Purpose: Number without value= inside template called within sorted for-each. -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(number)/ulist[2]/item[1]/p[1]/text()[5]" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(number)/ulist[1]/item[3]/p[1]/text()[1]" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(number)/p[2]/text()[3]" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(convert)/p[2]/text()[5]" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(convert)/ulist[1]/item[1]/p[1]/text()[1]" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="DocFrag" place="id(for-each)" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="DocFrag" place="id(sorting)" -->
  <!-- Scenario: operation="standard-XML" -->
  <!-- Elaboration: Numbering inside different template is still based on position
     of each node in the source tree, if there is no value attribute in xsl:number. -->

<xsl:template match="/">
  <out>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="doc/chapter">
  <list>
    <xsl:text>&#10;</xsl:text>
    <xsl:for-each select="note">
      <xsl:sort data-type="text" order="descending"/>
      <xsl:apply-templates select="." mode="numbering"/>
    </xsl:for-each>
  </list>
</xsl:template>

<xsl:template match="note" mode="numbering">
  <xsl:number level="single" from="chapter" format="(1) "/><xsl:value-of select="."/>
  <xsl:text>&#10;</xsl:text>
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
