<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: boolean58 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 4.2 String Functions  -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: If $x is bound to a node-set, then $x="foo" does not 
       mean the same as not($x!="foo"): the former is true if and only 
       if some node in $x has the string-value foo; the latter is true if 
       and only if all nodes in $x have the string-value foo. -->

<xsl:template match="doc">
	<out>

	  <xsl:variable name="x" select="av//*"/>
	  <xsl:value-of select="$x='foo'"/>
	  <xsl:value-of select="not($x!='foo')"/><xsl:text> </xsl:text>

	  <xsl:variable name="y" select="av//j"/>
	  <xsl:value-of select="$y='foo'"/>
	  <xsl:value-of select="not($y!='foo')"/>

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
