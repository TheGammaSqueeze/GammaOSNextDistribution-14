<?xml version='1.0'?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
				xmlns:ora="http://www.oracle.com/XSL/Transform/java/"
				extension-element-prefixes="ora"
				exclude-result-prefixes="ora">

  <!-- FileName: extend01 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 14 Extensions -->
  <!-- Purpose: Testing Conformance specific extension stuff. The top-level usage
       of a extension element is not really allowed and should be ignored. Therefore
       the first xsl:fallback should also be ignored.  -->
  <!-- Author: Paul Dick -->


<ora:output name="oout" method="html">
  <xsl:fallback>
	<xsl:text>THIS FALLBACK SHOULD BE IGNORED !! </xsl:text>
  </xsl:fallback>
</ora:output>

<xsl:template match="doc">
 <out>
	<xsl:variable name="filename" select="testfile.html"/>

	<ora:output  use="oout" href="{$filename}">
	  <xsl:fallback>
		<xsl:text>THIS FALLBACK OK !! </xsl:text>
  	  </xsl:fallback>
   	  <xsl:value-of select="element"/>
  	</ora:output>

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
