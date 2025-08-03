<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform" >

  <!-- FileName: copy27 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.3 -->
  <!-- Creator: Oliver Becker -->
  <!-- Purpose: Demonstrate copying a named template from the stylesheet
    into the result. From a thread on XSL-list 7/30/2001. -->

<xsl:template name="qq">
  <node attr="8"/>
</xsl:template>
 
<xsl:template match="/">
  <results>
    <usual-result>
      <xsl:call-template name="qq"/>
    </usual-result>
    <xsl:text>&#10;</xsl:text>
    <exotic-result>
      <xsl:copy-of select="document('')/*/xsl:template[@name='qq']/node()" />
    </exotic-result>
  </results>
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
