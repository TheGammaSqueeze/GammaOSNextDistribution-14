<xsl:transform
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 version="1.0"
>

  <!-- Test FileName: mk012.xsl -->
  <!-- Source Attribution: 
       This test was written by Michael Kay and is taken from 
       'XSLT Programmer's Reference' published by Wrox Press Limited in 2000;
       ISBN 1-861003-12-9; copyright Wrox Press Limited 2000; all rights reserved. 
       Now updated in the second edition (ISBN 1861005067), http://www.wrox.com.
       No part of this book may be reproduced, stored in a retrieval system or 
       transmitted in any form or by any means - electronic, electrostatic, mechanical, 
       photocopying, recording or otherwise - without the prior written permission of 
       the publisher, except in the case of brief quotations embodied in critical articles or reviews.
  -->
  <!-- Example: scene.xml, longest-speech.xsl -->
  <!-- Chapter/Page: 4-171 -->
  <!-- Purpose: Using recursion to process a node set -->

<xsl:template name="max">
<xsl:param name="list"/>
<xsl:choose>
<xsl:when test="$list">
   <xsl:variable name="first" select="count($list[1]/LINE)"/>
   <xsl:variable name="max-of-rest">
      <xsl:call-template name="max">
         <xsl:with-param name="list" select="$list[position()!=1]"/>
      </xsl:call-template>
   </xsl:variable>
   <xsl:choose>
   <xsl:when test="$first &gt; $max-of-rest">
      <xsl:value-of select="$first"/>
   </xsl:when>
   <xsl:otherwise>
      <xsl:value-of select="$max-of-rest"/>
   </xsl:otherwise>
   </xsl:choose>
</xsl:when>
<xsl:otherwise>0</xsl:otherwise>
</xsl:choose>
</xsl:template>

<xsl:template match="/">
Longest speech is <xsl:text/>
	<xsl:call-template name="max">
        <xsl:with-param name="list" select="//SPEECH"/>
	</xsl:call-template>
<xsl:text/> lines.  
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

</xsl:transform>
