<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ex="http://xml.apache.org/xalan"
                extension-element-prefixes="ex">

  <!-- FileName: libraryNodeset06 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.4 -->
  <!-- Creator: Joe Kesselman -->
  <!-- Purpose: Ensure that applying axes onto nodeset of local RTF gets the right one. -->

<xsl:output method="xml" indent="no" encoding="UTF-8"/>

<xsl:template match="/">
  <xsl:variable name="var1">
    <t1-far-north>
      <t1-north>
        <t1-near-north>
          <t1-far-west/>
          <t1-west/>
          <t1-near-west/>
          <center center-attr-1="c1" xmlns:n="http://example.com">Wrong variable, can you dig it?
            <t1-near-south>
              <t1-south>
                <t1-far-south/>
              </t1-south>
            </t1-near-south>
          </center>
          <t1-near-east/>
          <t1-east/>
          <t1-far-east/>
        </t1-near-north>
      </t1-north>
    </t1-far-north>
  </xsl:variable>

  <xsl:variable name="var2">
    <t2-far-north>
      <t2-north>
        <t2-near-north>
          <t2-far-west/>
          <t2-west/>
          <t2-near-west/>
          <center center-attr-1="c2" xmlns:n="http://example.com">Dig we must!
            <t2-near-south>
              <t2-south>
                <t2-far-south/>
              </t2-south>
            </t2-near-south>
          </center>
          <t2-near-east/>
          <t2-east/>
          <t2-far-east/>
        </t2-near-north>
      </t2-north>
    </t2-far-north>
  </xsl:variable>

  <out>
    <!-- Now, force evaluation of each of the above variables -->
    <junk>
      <xsl:text>$var1 summary: </xsl:text>
      <xsl:value-of select="$var1"/>
      <xsl:text>
</xsl:text>

      <xsl:text>$var2 summary: </xsl:text>
      <xsl:value-of select="$var2"/>
    </junk>
    <xsl:text>
</xsl:text>

    <xsl:text>The center nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>

</xsl:text>

    <xsl:text>W01: center/child::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/child::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W02: center/descendant::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/descendant::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W03: center/parent::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/parent::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W04: center/ancestor::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/ancestor::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W05: center/following-sibling::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/following-sibling::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W06: center/preceding-sibling::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/preceding-sibling::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W07: center/following::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/following::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W08: center/preceding::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/preceding::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W09: center/attribute::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/attribute::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W10: center/namespace::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/namespace::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W11: center/self::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/self::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W12: center/descendant-or-self::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/descendant-or-self::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>W13: center/ancestor-or-self::* nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/ancestor-or-self::*">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>

</xsl:text>
    <!-- Above was wildcard, now use name tests -->
    <xsl:text>N01: center/child::t2-near-south nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/child::t2-near-south">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N02: center/descendant::t2-south nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/descendant::t2-south">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N03: center/parent::t2-near-north nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/parent::t2-near-north">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N04: center/ancestor::t2-north nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/ancestor::t2-north">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N05: center/following-sibling::t2-east nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/following-sibling::t2-east">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N06: center/preceding-sibling::t2-west nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/preceding-sibling::t2-west">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N07: center/following::t2-east nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/following::t2-east">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N08: center/preceding::t2-west nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/preceding::t2-west">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N09: center/attribute::center-attr-1 nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/attribute::center-attr-1">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N10: center/self::center nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/self::center">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N11: center/descendant-or-self::t2-south nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/descendant-or-self::t2-south">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>

    <xsl:text>N12: center/ancestor-or-self::t2-north nodes in $var2 are </xsl:text>
    <xsl:for-each select="ex:nodeset($var2)//center/ancestor-or-self::t2-north">
      <xsl:value-of select="name(.)"/>
      <xsl:text>,</xsl:text>
    </xsl:for-each>
    <xsl:text>
</xsl:text>
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
