<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0">

  <!-- FileName: position80 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 2.2 -->
  <!-- Purpose: Ensure last() is working with unionpaths of ancestors. -->
  <!-- Creator: Scott Boag -->

<xsl:template match="/">
  <out>
    <xsl:for-each select="//section/title">
      Title= <xsl:value-of select="."/>
      <xsl:variable name="size">
        <xsl:value-of select="count(ancestor::section
          |ancestor::simplesect|ancestor::articleinfo)"/>
      </xsl:variable>
      <xsl:for-each select="(ancestor::section|ancestor::simplesect
        |ancestor::articleinfo)[last()]">
        <name>
          <xsl:attribute name="last-of">
            <xsl:value-of select="$size"/>
          </xsl:attribute>
          <xsl:value-of select="name(.)"/>
        </name>
      </xsl:for-each>
    </xsl:for-each>
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
