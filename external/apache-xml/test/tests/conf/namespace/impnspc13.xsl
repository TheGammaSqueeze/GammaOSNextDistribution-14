<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="xml"/>

  <!-- FileName: impnspc13 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.1 XSLT Namespace -->
  <!-- Purpose: This stylesheet is being imported by namespace13 which has the namespace
       prefix set to 'ped'. Testing that this setup with two different namespaces
       is allowed. -->

<xsl:template match="doc" priority="1.0">
  <out>
	<xsl:value-of select="'In Import: Testing '"/>
	<xsl:for-each select="*">
		<xsl:value-of select="."/><xsl:text> </xsl:text>		
	</xsl:for-each>

	<xsl:text>&#010;&#013;</xsl:text>
	<xsl:call-template name="ThatTemp">
		<xsl:with-param name="sam">quos</xsl:with-param>
	</xsl:call-template>

  </out>
</xsl:template>

<xsl:template name="ThatOtherTemp">
	<xsl:param name="sam">bo</xsl:param>
	<xsl:copy-of select="$sam"/>
</xsl:template>

<xsl:template name="ThatForthTemp">
  <xsl:param name="sam">bo</xsl:param>
	Imported xmlns:xsl <xsl:copy-of select="$sam"/>
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
