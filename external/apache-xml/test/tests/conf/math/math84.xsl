<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: math84 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19990922 -->
  <!-- Section: 4.4 Number Functions-->
  <!-- Creator: Paul Dick -->
  <!-- Purpose: Test of sum(). -->

<xsl:template match="doc">
  <out><xsl:text>&#10;</xsl:text>
    <xsl:variable name="rtf" select="av//h"/>
	<xsl:copy-of select="$rtf"/><xsl:text>&#10;</xsl:text>
    <xsl:value-of select="sum($rtf)"/><xsl:text>&#10;</xsl:text>
	<xsl:value-of select="$rtf"/><xsl:text>&#10;</xsl:text>
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
