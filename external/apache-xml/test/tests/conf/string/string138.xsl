<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: string138 -->
  <!-- Document: http://www.w3.org/TR/xpath -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 4.2 String Functions -->
  <!-- Creator: David Marston -->
  <!-- Purpose: Use 'translate()' to change quotes and apostrophes to other characters. -->

<xsl:template match="/doc">
  <out>
    <!-- The input string to translate() contains both a quote and an apostrophe.
      Since only one of those two can be treated as data in one string constant,
      we build up the string from two variables, using opposite double-quoting on each. -->
    <xsl:variable name="pt1" select='"aqu&apos;"'/>
    <xsl:variable name="pt2" select="'&quot;eos'"/>
    <xsl:value-of select="translate(a,concat($pt1,$pt2),'AQU-+EOS')"/>
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
