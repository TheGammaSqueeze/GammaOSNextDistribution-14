<xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    version="1.0"
>

  <!-- Test FileName: mk051.xsl -->
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
  <!-- Example: scene.xml, scene.xsl -->
  <!-- Chapter/Page: 8-544 -->
  <!-- Purpose: Illustrate a rules-based stylesheet -->
  
<xsl:variable name="backcolor" select="'#FFFFCC'" />

<xsl:template match="SCENE|PROLOGUE|EPILOGUE">
    <HTML>
    <HEAD>
        <TITLE><xsl:value-of select="TITLE"/></TITLE>
    </HEAD>
    <BODY BGCOLOR='{$backcolor}'>
        <xsl:apply-templates/>
    </BODY>
    </HTML>
</xsl:template>

<xsl:template match="SPEECH">
    <TABLE><TR>
    <TD WIDTH="160" VALIGN="TOP">
	<xsl:apply-templates select="SPEAKER"/>
    </TD>
    <TD VALIGN="TOP">
    <xsl:apply-templates select="STAGEDIR|LINE"/>
    </TD>
	</TR></TABLE>
</xsl:template>

<xsl:template match="TITLE">
    <H1><CENTER>
	<xsl:apply-templates/>
	</CENTER></H1><HR/>
</xsl:template>

<xsl:template match="SPEAKER">
    <B>
    <xsl:apply-templates/>
    <xsl:if test="not(position()=last())"><BR/></xsl:if>
    </B>
</xsl:template>

<xsl:template match="SCENE/STAGEDIR">
    <CENTER><H3>
	<xsl:apply-templates/>
	</H3></CENTER>
</xsl:template>

<xsl:template match="SPEECH/STAGEDIR">
    <P><I>
	<xsl:apply-templates/>
	</I></P>
</xsl:template>

<xsl:template match="LINE/STAGEDIR">
     [ <I>
	<xsl:apply-templates/>
	</I> ] 
</xsl:template>

<xsl:template match="SCENE/SUBHEAD">
    <CENTER><H3>
	<xsl:apply-templates/>
	</H3></CENTER>
</xsl:template>

<xsl:template match="SPEECH/SUBHEAD">
    <P><B>
	<xsl:apply-templates/>
	</B></P>
</xsl:template>

<xsl:template match="LINE">
	<xsl:apply-templates/>
	<BR/>
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
