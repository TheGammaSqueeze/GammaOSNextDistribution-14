<?xml version="1.0"?> 
<xsl:stylesheet version="1.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:math="http://exslt.org/math"
                extension-element-prefixes="math">

<!-- Test math:highest() -->

<xsl:template match="doc">
  <out>
     This is for testing the node-set of doc/b with highest value in it.  That tree would be:
     <xsl:value-of select="math:max(math:highest(b))"/>
     This is for testing the node-set of b1/b with highest value in it.  That tree would be:
     <xsl:value-of select="math:max(math:highest(//b1/b))"/>
     This is for testing the node-set of b2/b with highest value in it.  That tree would be:
     <xsl:value-of select="math:max(math:highest(//b2/b))"/>
     This is for testing the node-set of b3/b with highest value in it.  That tree would be:
     <xsl:value-of select="math:max(math:highest(b3/b))"/>
     This is for testing the node-set of //b with highest value in it.  That tree would be:
     <xsl:value-of select="math:max(math:highest(//b))"/>
     
     This is for testing the node-set of c with highest value in it.  That tree would be:
     <xsl:value-of select="math:max(math:highest(//c))"/>
     This is for testing the node-set of d with highest value in it.  That tree would be:
     <xsl:value-of select="math:max(math:highest(//d))"/>
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
