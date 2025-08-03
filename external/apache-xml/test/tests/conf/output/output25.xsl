<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">
<xsl:output method="text"/>

  <!-- FileName: OUTP25 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 16.3 Text Output Method -->
  <!-- Purpose: ??? -->

<xsl:template match="Sprs">
<s3 title="Known bugs:">
 <p>We are aware of the following bugs (SPR ID# and description):</p><xsl:text>
</xsl:text>
   <ul>
	<xsl:apply-templates select="Spr/State[. = 'Open']"/><xsl:text>&#010;</xsl:text>
   </ul></s3>
</xsl:template>

<xsl:template match="*">
	<li>
	<xsl:value-of select="preceding-sibling::*[2]"/><xsl:text>: </xsl:text>
	<xsl:value-of select="following-sibling::*[2]"/><xsl:text>. ( </xsl:text>
	<xsl:value-of select="following-sibling::*[1]"/><xsl:text>)
</xsl:text>
	<br/><br/></li>
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
