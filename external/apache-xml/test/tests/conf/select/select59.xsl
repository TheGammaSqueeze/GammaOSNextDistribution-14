<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: select59 -->
  <!-- Document: http://www.w3.org/TR/Xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.3 Node Sets -->
  <!-- Purpose: test union operator using overlapping node-sets. Results should
       always be output in doc order regardless of order of select attribute. -->
  <!-- Creator: Carmelo Montanez (original) --><!-- Expression019 in NIST suite -->
  <!-- Creator: Paul Dick (this version) -->

<xsl:key name="which" match="child" use="@wide|@deep"/>
<xsl:key name="one" match="child" use="@deep"/>
<xsl:key name="two" match="child" use="@wide"/>

<xsl:template match="doc">
  <out><xsl:text>
</xsl:text>
<xsl:apply-templates select = "child[@wide='3']|child[@deep='3']"/>,
<xsl:apply-templates select = "child[@deep='3']|child[@wide='3']"/>,
<xsl:apply-templates select = "key('which','3')"/>,
<xsl:apply-templates select = "key('one','3') | key('two','3')"/>,
<xsl:apply-templates select = "child[@wide='3'] | key('one','3')"/>,
<xsl:apply-templates select = "key('two','3') | document('select59.xml')/child[@wide='3'] | child[@deep='3']"/>,
</out>
</xsl:template>

<xsl:template match = "*">
<xsl:value-of select = "."/>
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
