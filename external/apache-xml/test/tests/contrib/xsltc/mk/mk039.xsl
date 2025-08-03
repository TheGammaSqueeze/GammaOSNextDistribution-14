<?xml version="1.0" ?>
<xsl:stylesheet 
 xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
 version="1.0"
>

  <!-- Test FileName: mk039.xsl -->
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
  <!-- Example: resorts.xml, resorts.xsl -->
  <!-- Chapter/Page: 7-464 -->
  <!-- Purpose: Using generate id to create links  -->

<xsl:template match="/">
<html>
<body>
   <h1>Hotels</h1>
   <xsl:for-each select="//hotel">
   <xsl:sort select="stars" order="descending" data-type="number"/>
      <h2><xsl:value-of select="name"/></h2>
      <p>Address: <xsl:value-of select="address"/></p>
      <p>Stars: <xsl:value-of select="stars"/></p>
      <p>Resort: <a href="#{generate-id(parent::resort)}">
            <xsl:value-of select="parent::resort/name"/></a></p>
   </xsl:for-each>

   <h1>Resorts</h1>
   <xsl:for-each select="//resort">
      <h2><a name="{generate-id()}">
         <xsl:value-of select="name"/>
      </a></h2>
      <p><xsl:value-of select="details"/></p>
   </xsl:for-each>
</body>
</html>
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
