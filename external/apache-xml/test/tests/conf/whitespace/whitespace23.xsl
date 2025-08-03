<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: whitespace23 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.4 Whitespace Stripping -->
  <!-- Creator: Scott Boag (in response to problem reported by "Carsten Ziegeler" <cziegeler@sundn.de>) -->
  <!-- Purpose: Another test for the normalize-space function, this one really testing handling of the newline. -->

<xsl:template match = "doc">
   <out>
	<xsl:for-each select="link">
		 <a>
		 		 <xsl:attribute name="href"><xsl:value-of
                                select="normalize-space(url)"/></xsl:attribute>
		 		 <xsl:value-of select="normalize-space(url)"/>
		 </a>
		 <xsl:text>&#10;</xsl:text>
	</xsl:for-each>

	<xsl:for-each select="link">
		 <a href="{normalize-space(url)}">
		 		 <xsl:value-of select="normalize-space(url)"/>
		 </a>
		 <xsl:text>&#10;</xsl:text>
	</xsl:for-each>

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
