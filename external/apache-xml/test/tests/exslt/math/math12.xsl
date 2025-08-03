<?xml version="1.0"?> 
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:math="http://exslt.org/math"
                xmlns:common="http://exslt.org/common"
                extension-element-prefixes="math">

<!-- Test math:max() -->

<xsl:template match="doc">
  <out>
     This is for finding the maximum value in the node-set b and printing it:
     <xsl:value-of select="math:max(//b)"/>
     This is for finding the maximum value in the node-set c and printing it:
     <xsl:value-of select="math:max(//c)"/>
     This is for finding the maximum value in the node-set d and printing it:
     <xsl:value-of select="math:max(//d)"/>
     
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
