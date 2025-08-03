<?xml version="1.0"?> 

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
xmlns:set="http://exslt.org/sets" >

<!-- Test set:difference -->

<xsl:variable name="i" select="//city[contains(@name,'i')]"/>
<xsl:variable name="e" select="//city[contains(@name,'e')]"/>
<xsl:variable name="B" select="//city[contains(@name,'B')]"/>

<xsl:template match="/">
  <out>
    Containing i and no e:
    <xsl:for-each select="set:difference($i, $e)">
         <xsl:value-of select="@name"/>;
    </xsl:for-each> 
    Containing e and no i:
    <xsl:for-each select="set:difference($e, $i)">
         <xsl:value-of select="@name"/>;
    </xsl:for-each>
    Containing B and no i and no e:
    <xsl:for-each select="set:difference(set:difference($B,$i),$e)">
         <xsl:value-of select="@name"/>;
    </xsl:for-each>
    
    <!-- test difference on empty sets -->

    Containing i:
    <xsl:for-each select="set:difference($i, /..)">
         <xsl:value-of select="@name"/>;
    </xsl:for-each>
    Containing B:
    <xsl:for-each select="set:difference($B, /..)">
         <xsl:value-of select="@name"/>;
    </xsl:for-each>
    Empty set:
    <xsl:for-each select="set:difference(/.., $i)">
         <xsl:value-of select="@name"/>;
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
