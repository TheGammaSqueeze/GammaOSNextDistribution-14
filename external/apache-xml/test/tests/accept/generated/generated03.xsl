<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: generated03 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.4 -->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Test generate-id() when nodes are coming from different documents. -->
  <!-- Elaboration: All IDs should be distinct. The first for-each prints out info about the document
    and node value. The second loop prints out the ID. Exact strings will vary by processor. All should
    meet the constraints of XML names. Use this test to catch unexplained changes in the naming scheme. -->

<xsl:output method="xml" encoding="UTF-8" indent="no"/>

<xsl:template match="doc">
  <out>
   <values>
    <xsl:for-each select="document(a)//body">
      <xsl:value-of select="."/><xsl:text>,  </xsl:text>
    </xsl:for-each></values>
    <xsl:text>&#10;</xsl:text>
   <ids>
    <xsl:for-each select="document(a)//body">
      <xsl:value-of select="generate-id(.)"/><xsl:text>,  </xsl:text>
    </xsl:for-each></ids>
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
