<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variable18 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.6 Passing Parameters to Templates.  -->
  <!-- Purpose: Test param being set to default in a named template. -->
  <!-- Author: David Marston -->

<xsl:template match="/">
  <out>
    <xsl:call-template name="myTmpl">
      <!-- If we had a with-param here, we could change the value of "bar". -->
    </xsl:call-template>
  </out>
</xsl:template>

<xsl:template name="myTmpl">
  <xsl:param name="bar">defaultVal</xsl:param>
    <foo>
      <xsl:value-of select="$bar"/>
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
