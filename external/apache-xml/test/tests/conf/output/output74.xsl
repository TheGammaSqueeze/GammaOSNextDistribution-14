<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<xsl:output method="html"/>

  <!-- FileName: output74 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 16.4 Disable output escaping. -->
  <!-- Purpose: Spec states:It is an error for output escaping to be disabled 
       for a text node that is used for something other than a text node in the 
       result tree. Thus, it is an error to disable output escaping for an 
       xsl:value-of or xsl:text element that is used to generate the string-value 
       of a comment, processing instruction or attribute node;. OUTPUT = HTML -->
  <!-- Author: Paul Dick -->

<xsl:template match="doc">
 <html>
  <out1>
	<xsl:attribute name="attrib1">
		<xsl:text disable-output-escaping="no">_&lt;Whoa-No&gt;_</xsl:text>
	</xsl:attribute>
	<xsl:attribute name="attrib2">
		<xsl:value-of select="a" disable-output-escaping="no"/>
	</xsl:attribute></out1>

  <!-- This is the error case. It should come out as doe="no" -->
  <out2>  
	<xsl:attribute name="attrib3">
		<xsl:text disable-output-escaping="yes">_&lt;Whoa-Yes&gt;_</xsl:text>
	</xsl:attribute>
	<xsl:attribute name="attrib4">
		<xsl:value-of select="a" disable-output-escaping="yes"/>
	</xsl:attribute></out2 >

  <h1>
  	<xsl:attribute name="title">
		<xsl:text disable-output-escaping="yes">_&lt;Yes-Contacts&gt;_</xsl:text>
	</xsl:attribute>People</h1>

  <frame>
  	<xsl:attribute name="scrolling">yes</xsl:attribute>
	<xsl:attribute name="name">
  		<xsl:text disable-output-escaping="yes">_&lt;this&gt;_</xsl:text>
	</xsl:attribute></frame>

  <out3>
	<xsl:value-of select="a" disable-output-escaping="yes"/></out3>

 </html>
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
