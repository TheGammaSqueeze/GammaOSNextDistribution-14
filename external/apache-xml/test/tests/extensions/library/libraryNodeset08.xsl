<?xml version="1.0"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
                xmlns:ex="http://xml.apache.org/xalan"
                extension-element-prefixes="ex">

  <!-- FileName: libraryNodeset08 -->
  <!-- Document: http://www.w3.org/TR/xslt -->
  <!-- DocVersion: 19991116 -->
  <!-- Section: 11.4 -->
  <!-- Creator: Joe Kesselman -->
  <!-- Purpose: Try paths from nodeset of local RTF; ensure we get the right one. -->

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

    <!-- Now, traverse some axes -->
    <xsl:apply-templates select="ex:nodeset($var2)//t2-north"/>
  </out>
</xsl:template>

<xsl:template match="t2-north">
  <!-- DS means the location path is optimizable as a single descendant iterator. -->
DS   1. AC: <xsl:value-of select="name(/descendant-or-self::t2-north)"/>
DS   2. AD: <xsl:value-of select="name(/descendant::t2-near-north)"/>
DS   3. BC: <xsl:value-of select="name(self::node()/descendant-or-self::t2-north)"/>
DS   4. BD: <xsl:value-of select="name(self::node()/descendant::t2-near-north)"/>
NDS  5. CC: <xsl:value-of select="name(descendant-or-self::t2-north/descendant-or-self::t2-north)"/>
NDS  6. CD: <xsl:value-of select="name(descendant-or-self::t2-north/descendant::t2-near-north)"/>
NDS  7. CE: <xsl:value-of select="name(descendant-or-self::t2-north/child::t2-near-north)"/>
NDS  8. DC: <xsl:value-of select="name(descendant::t2-near-north/descendant-or-self::t2-near-north)"/>
NDS  9. DD: <xsl:value-of select="name(descendant::t2-near-north/descendant::t2-far-west)"/>

NDS 10. ACC: <xsl:value-of select="name(/descendant-or-self::t2-north/descendant-or-self::t2-north)"/>
NDS 11. ACE: <xsl:value-of select="name(/descendant-or-self::t2-north/child::t2-near-north)"/>
NDS 12. ADC: <xsl:value-of select="name(/descendant::t2-near-north/descendant-or-self::t2-near-north)"/>
NDS 13. BCC: <xsl:value-of select="name(self::node()/descendant-or-self::t2-north/descendant-or-self::t2-north)"/>
NDS 14. BCE: <xsl:value-of select="name(self::node()/descendant-or-self::t2-north/child::t2-near-north)"/>
NDS 15. BDC: <xsl:value-of select="name(self::node()/descendant::t2-near-north/descendant-or-self::t2-far-west)"/>
NDS 16. BDE: <xsl:value-of select="name(self::node()/descendant::t2-near-north/child::t2-far-west)"/>
NDS 17. CCC: <xsl:value-of select="name(descendant-or-self::t2-north/descendant-or-self::t2-north/descendant-or-self::t2-north)"/>
NDS 18. CCE: <xsl:value-of select="name(descendant-or-self::t2-north/descendant-or-self::t2-north/child::t2-near-north)"/>
NDS 19. CDC: <xsl:value-of select="name(descendant-or-self::t2-north/descendant::t2-near-north/descendant-or-self::t2-near-north)"/>
NDS 20. CDE: <xsl:value-of select="name(descendant-or-self::t2-north/descendant::t2-near-north/child::t2-far-west)"/>
NDS 21. CEC: <xsl:value-of select="name(descendant-or-self::t2-north/child::t2-near-north/descendant-or-self::t2-near-north)"/>
NDS 22. CEE: <xsl:value-of select="name(descendant-or-self::t2-north/child::t2-near-north/child::t2-far-west)"/>
NDS 23. DCC: <xsl:value-of select="name(descendant::t2-near-north/descendant-or-self::t2-near-north/descendant-or-self::t2-near-north)"/>
NDS 24. DCE: <xsl:value-of select="name(descendant::t2-near-north/descendant-or-self::t2-near-north/child::t2-far-west)"/>
NDS 25. DDC: <xsl:value-of select="name(descendant::t2-near-north/descendant::t2-far-west/descendant-or-self::t2-far-west)"/>

DS  26. CC: <xsl:value-of select="name(descendant-or-self::node()/descendant-or-self::t2-north)"/>
DS  27. CD: <xsl:value-of select="name(descendant-or-self::node()/descendant::t2-near-north)"/>
DS  28. CE: <xsl:value-of select="name(descendant-or-self::node()/child::t2-near-north)"/>
DS  29. DC: <xsl:value-of select="name(descendant::node()/descendant-or-self::t2-near-north)"/>
DS  30. DD: <xsl:value-of select="name(descendant::node()/descendant::t2-far-west)"/>

DS  31. ACC: <xsl:value-of select="name(/descendant-or-self::node()/descendant-or-self::t2-north)"/>
DS  32. ACE: <xsl:value-of select="name(/descendant-or-self::node()/child::t2-near-north)"/>
DS  33. ADC: <xsl:value-of select="name(/descendant::node()/descendant-or-self::t2-near-north)"/>
DS  34. BCC: <xsl:value-of select="name(self::node()/descendant-or-self::node()/descendant-or-self::t2-north)"/>
DS  35. BCE: <xsl:value-of select="name(self::node()/descendant-or-self::node()/child::t2-near-north)"/>
DS  36. BDC: <xsl:value-of select="name(self::node()/descendant::node()/descendant-or-self::t2-far-west)"/>
DS  37. BDE: <xsl:value-of select="name(self::node()/descendant::node()/child::t2-far-west)"/>
DS  38. CCC: <xsl:value-of select="name(descendant-or-self::node()/descendant-or-self::node()/descendant-or-self::t2-north)"/>
DS  39. CCE: <xsl:value-of select="name(descendant-or-self::node()/descendant-or-self::node()/child::t2-near-north)"/>
DS  40. CDC: <xsl:value-of select="name(descendant-or-self::node()/descendant::node()/descendant-or-self::t2-near-north)"/>
DS  41. CDE: <xsl:value-of select="name(descendant-or-self::node()/descendant::node()/child::t2-far-west)"/>
DS  42. CEC: <xsl:value-of select="name(descendant-or-self::node()/child::node()/descendant-or-self::t2-near-north)"/>
DS  43. CEE: <xsl:value-of select="name(descendant-or-self::node()/child::node()/child::t2-far-west)"/>
DS  44. DCC: <xsl:value-of select="name(descendant::node()/descendant-or-self::node()/descendant-or-self::t2-near-north)"/>
DS  45. DCE: <xsl:value-of select="name(descendant::node()/descendant-or-self::node()/child::t2-far-west)"/>
DS  46. DDC: <xsl:value-of select="name(descendant::node()/descendant::node()/descendant-or-self::t2-far-west)"/>
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
