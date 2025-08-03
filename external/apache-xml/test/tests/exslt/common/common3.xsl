<?xml version="1.0"?> 

<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
xmlns:exslt="http://exslt.org/common" >

<!-- Test exslt:object-type -->

<xsl:variable name="tree">
<a>
  <b>
    <c>
      <d/>
      <e/>
    </c>
  </b>
</a>
</xsl:variable>

<xsl:variable name="string" select="'fred'"/>
<xsl:variable name="number" select="93.7"/>
<xsl:variable name="boolean" select="true()"/>
<xsl:variable name="node-set" select="//*"/>

<xsl:template match="/">
  <out>:
    <xsl:value-of select="exslt:object-type($string)"/>;
    <xsl:value-of select="exslt:object-type($number)"/>;
    <xsl:value-of select="exslt:object-type($boolean)"/>;
    <xsl:value-of select="exslt:object-type($node-set)"/>;
    <xsl:value-of select="exslt:object-type($tree)"/>;
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
