<?xml version="1.0"?> 
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: OUTPerr14 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 16.3 Text Output Method -->
  <!-- Purpose: Attempt text output of characters above 127 when encoding doesn't support them. -->
  <!-- "If the result tree contains a character that cannot be represented in the encoding
       that the XSLT processor is using for output, the XSLT processor should signal an error." -->
  <!-- ExpectedException: Attempt to output character not represented in specified encoding. -->
  <!-- ExpectedException: Attempt to output character of integral value 264 that is not represented in specified output encoding of US-ASCII. -->

<xsl:output method="text" encoding="US-ASCII"/>

<xsl:template match="doc">
  <xsl:apply-templates select="dat"/><xsl:text>
</xsl:text>
</xsl:template>

<xsl:template match="dat">
  <xsl:text>&#264;</xsl:text><xsl:value-of select="."/>
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
