<?xml version="1.0" encoding="ISO-8859-1"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: PREDICATE37 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 3.4 -->
  <!-- Purpose: Show that we can limit match to non-empty elements. -->

<xsl:template match="/" >
  <out>
    <xsl:apply-templates/>
  </out>
</xsl:template>

<xsl:template match="a" >
  <!-- If the node 'a' has a child 'b' that has a value in it,
  then show it. Otherwise don't show it. -->

  <xsl:if test='b[not(.="")]' >( <xsl:value-of select="b" /> )</xsl:if>

  <!-- If the node 'a' has a child 'c' that has a value in it,
  then show it. Otherwise don't show it. -->

  <xsl:if test='c[not(.="")]' >( <xsl:value-of select="c" /> )</xsl:if>
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
