<?xml version="1.0"?> 

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
xmlns:set="http://exslt.org/sets" >

<!-- Test set:has-same-node -->

<xsl:variable name="a1" select="//city[@name='Vienna' or @name='Salzburg']"/>
<xsl:variable name="a2" select="//city[@country='Austria']"/>

<xsl:template match="/">
  <out>
    Test has-same-node() between two intersecting sets:
    <xsl:if test="set:has-same-node($a1,$a2)">OK</xsl:if>;
    Test has-same-node() between two non-intersecting sets:
    <xsl:if test="not(set:has-same-node($a1,//city/@name))">OK</xsl:if>;
    Test has-same-node() between two identical sets of namespace nodes:
    <xsl:if test="set:has-same-node((//city[1])/namespace::*,(//city[1])/namespace::*)">OK</xsl:if>;        
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
