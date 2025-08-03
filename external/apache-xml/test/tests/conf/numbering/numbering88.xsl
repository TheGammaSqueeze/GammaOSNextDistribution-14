<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- CaseName: numbering88 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Show discrepancies between number and position. -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(number)/ulist[2]/item[1]/p[1]/text()[5]" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(number)/ulist[2]/item[3]/p[1]/text()[6]" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(number)/ulist[1]/item[2]/p[1]/text()[3]" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(number)/ulist[1]/item[3]/p[1]/text()[1]" -->
  <!-- SpecCitation: Rec="XSLT" Version="1.0" type="OASISptr1" place="id(convert)/p[2]/text()[3]" -->
  <!-- Scenario: operation="standard-XML" -->
  <!-- Elaboration: While xsl:number always shows the number of the chapter among all chapters,
       position() in the outer case is the position of the chapter among all children of doc,
       position() in the inner case is the position of the chapter within the select="." set
       that contains just one element. -->

<xsl:template match="doc">
  <out>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="chapter">
  <xsl:element name="chap">
    <xsl:attribute name="number">
      <xsl:number/>
    </xsl:attribute>
    <xsl:attribute name="position">
      <xsl:value-of select="position()"/>
    </xsl:attribute>
    <xsl:value-of select="note[1]"/>
    <xsl:apply-templates select="." mode="repeat"/>
  </xsl:element>
</xsl:template>

<xsl:template match="chapter" mode="repeat">
  <xsl:element name="sel">
    <xsl:attribute name="number">
      <xsl:number/>
    </xsl:attribute>
    <xsl:attribute name="position">
      <xsl:value-of select="position()"/>
    </xsl:attribute>
  </xsl:element>
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
