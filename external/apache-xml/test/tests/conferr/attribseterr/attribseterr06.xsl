<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: attribseterr06 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 7.1.4 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test placement of attribute-set inside a template, which is illegal. -->
  <!-- ExpectedException: xsl:attribute-set is not allowed inside a template! -->
  <!-- ExpectedException: XSLT: (StylesheetHandler) xsl:attribute-set is not allowed inside a template! -->
  <!-- ExpectedException: xsl:attribute-set is not allowed in this position in the stylesheet! -->
<xsl:template match="/">
  <out>
    <xsl:attribute-set name="set2">
      <xsl:attribute name="text-decoration">underline</xsl:attribute>
    </xsl:attribute-set>
    <test1 xsl:use-attribute-sets="set1">This should fail</test1>
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
