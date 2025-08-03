<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

  <!-- FileName: variable09 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.2 Values of Variables and Parameters -->
  <!-- Purpose: Test for handling of number used as positional index by setting
        variable using select attribute.  Reference as [$n] -->
  <!-- Author: Paul Dick -->
  <!-- Note: When a variable is used to select nodes by position, 
             be careful not to do: 
             <xsl:variable name="n">2</xsl:variable>
                ...
             <xsl:value-of select="item[$n]"/>
             This will output the value of the first item element, 
             because the variable n will be bound to a result tree fragment, 
             not a number. Instead, do either 

             <xsl:variable name="n" select="2"/>
                ...
             <xsl:value-of select="item[$n]"/>
                or 
             <xsl:variable name="n">2</xsl:variable>
                ...
             <xsl:value-of select="item[position()=$n]"/>    -->

<xsl:template match="doc">
  <xsl:variable name="n" select="2"/>
  <out>
    <xsl:value-of select="item[$n]"/>
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
