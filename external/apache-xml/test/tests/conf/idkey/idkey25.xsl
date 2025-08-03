<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: idkey25 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 12.2 -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Test for variable as first argument to key(). -->

<xsl:output indent="yes"/>

<xsl:variable name="keysp">bigspace</xsl:variable>

<xsl:key name="bigspace" match="div" use="subdiv/title" />
<xsl:key name="smallspace" match="subdiv" use="title" />
<xsl:key name="filterspace" match="div[@allow='yes']" use="subdiv/title" />

<xsl:template match="doc">
 <root>
  Using keyspace <xsl:value-of select="$keysp"/>...
  <xsl:value-of select="key($keysp, 'Introduction')/subdiv/p"/>
  <xsl:value-of select="key($keysp, 'Stylesheet Structure')/subdiv/p"/>
  <xsl:value-of select="key($keysp, 'Expressions')/subdiv/p"/>
 </root>
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
