<?xml version="1.0" encoding="iso-8859-1"?>
<xsl:transform
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 version="1.0"
>

  <!-- Test FileName: mk041.xsl -->
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
  <!-- Example: issue-dates.xml, format-dates.xsl -->
  <!-- Chapter/Page: 7-476 -->
  <!-- Purpose: Using lang function for localizing dates -->

<xsl:output encoding="iso-8859-1"/>

<data xmlns="data.uri">
<months xml:lang="en">
   <m>January</m><m>February</m><m>March</m><m>April</m>
   <m>May</m><m>June</m><m>July</m><m>August</m>
   <m>September</m><m>October</m><m>November</m><m>December</m>
</months>
<months xml:lang="fr">
   <m>Janvier</m><m>Février</m><m>Mars</m><m>Avril</m>
   <m>Mai</m><m>Juin</m><m>Juillet</m><m>Août</m>
   <m>Septembre</m><m>Octobre</m><m>Novembre</m><m>Décembre</m>
</months>
<months xml:lang="de">
   <m>Januar</m><m>Februar</m><m>März</m><m>April</m>
   <m>Mai</m><m>Juni</m><m>Juli</m><m>August</m>
   <m>September</m><m>Oktober</m><m>November</m><m>Dezember</m>
</months>
</data>

<xsl:param name="language" select="'en'"/>

<xsl:template match="iso-date">
<date xmlns:data="data.uri" xsl:exclude-result-prefixes="data">
   <xsl:value-of select="substring(., 7, 2)"/>
   <xsl:text> </xsl:text>
   <xsl:variable name="month" select="number(substring(.,5,2))"/>
   <xsl:value-of select="document('')/*/data:data/data:months[lang($language)]/data:m[$month]"/>
   <xsl:text> </xsl:text>
   <xsl:value-of select="substring(., 1, 4)"/>
</date>
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
