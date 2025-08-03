<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
				version="2.0">

  <!-- FileName: Ver07 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.5 Forwards-Compatible Processing  -->
  <!-- Creator: Shane Curcuru -->
  <!-- Purpose: Test the basics of the XSLT version declaration. Should not raise an error. -->
  <!-- Note that this test obviously needs updating as soon as we support XSLT 2.0! -->

  <xsl:template match="/">
    <out>
      <xsl:text>xsl:choose when test="system-property('xsl:version') &gt;= 2.0"</xsl:text>
      <choose>
      <xsl:choose>
        <xsl:when test="system-property('xsl:version') &gt;= 2.0">
          <xsl:text>Hey! Call the 2.0 feature!</xsl:text>
          <xsl:exciting-new-2.0-feature/>
        </xsl:when>
        <xsl:otherwise>
          <xsl:text>Hey! 2.0 features are not supported!</xsl:text>
        </xsl:otherwise>
      </xsl:choose>
      </choose>
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
