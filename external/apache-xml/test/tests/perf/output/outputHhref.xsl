<?xml version="1.0" encoding="ISO-8859-1"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="html"/>

  <!-- Purpose: ESC of non-ASCII chars in URI attribute	values using method 
       cited in Section B.2.1 of HTML 4.0 Spec. -->

<xsl:template match="doc">
  <html>
  <head>
    <title>
      <xsl:value-of select="header"/>
    </title>
  </head>
  <!-- Note the body/@background should be escaped as well, I think -->
  <body background="file&apos;&#037;.gif">
    <xsl:apply-templates select="list"/>
  </body>
  </html>
</xsl:template>

<xsl:template match="list">
  <h1>List</h1>
  <xsl:apply-templates select="list | item"/>
</xsl:template>

<!-- A simplistic template for testing performance of HTML escaping; 
     vaguely like what you might see in real life.  Includes various 
     avt's interspersed with escaped characters and one non-escaped 
     attribute font/@color.
-->
<xsl:template match="item">
  <br/>
  spacer
  
    <p>1. "&amp;"  <A HREF="&amp;{.}"><xsl:copy-of select="text()"/></A></p>
    <p>2. "&lt;"   <img src="&lt;"></img></p>
    <p>3. "&gt;"   <IMG src="&gt;"></IMG></p>
    <p>4. "&quot;" <img SRC="&quot;{text()}"/></p>
    <p>5. "&apos;" <font color="&apos;"><xsl:copy-of select="text()"/></font></p>
    <p>6. "&#169;" <a HREF="&#169;"><xsl:value-of select="text()"/></a></p>
    <p>7. "&#035;" <A href='&amp;{{text()}}between&#035;after'>Note the amp-double-braces should be escaped differently</A></p>
    <p>8. "&#165;" <A href="&#165;after"><xsl:value-of select="text()"/></A></p>
    <p>9. "&#032;" <a href="before&#032;"><xsl:copy-of select="text()"/></a></p>
    <p>10."&#037;" <IMG SRC="{.}&#037;"><xsl:value-of select="."/></IMG></p>
    <p>11."&#009;" <A href="beforeand&#009;after">No value</A></p>
    <p>12."&#127;" <A HREF="{.}&#127;after"><xsl:value-of select="."/></A></p>
    <p>13."&#209;" <A href="&#209;">plain text</A></p>
    <P>14."&#338;" <A href="&#338;">more plain text</A></P>
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
