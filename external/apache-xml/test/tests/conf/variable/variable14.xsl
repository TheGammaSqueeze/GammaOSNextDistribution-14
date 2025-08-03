<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variable14 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 Passing Parameters to Templates. -->
  <!-- Purpose: Verify that a variable defined within a xsl:with-param content
                is scoped correctly. Idea sent in on xalan-dev list. We should
                recognize 'test' within the with-param statement. 
                The named template 'foo' really plays no part here. -->
  <!-- Author: Paul Dick, based on posting to xalan-dev list -->

<xsl:template match="doc">
  <xsl:call-template name="foo">
    <xsl:with-param name="bar">
      <xsl:variable name="test">Test</xsl:variable>
      <xsl:value-of select="$test"/>
    </xsl:with-param>
  </xsl:call-template>
</xsl:template>

<xsl:template name="foo">
  <xsl:param name="bar"/>
  <foo>
    <xsl:copy-of select="$bar"/>
  </foo>
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
