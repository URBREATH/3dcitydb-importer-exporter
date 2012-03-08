-- RASTER_RELIEF.sql
--
-- Authors:     Prof. Dr. Lutz Pluemer <pluemer@ikg.uni-bonn.de>
--              Prof. Dr. Thomas H. Kolbe <kolbe@igg.tu-berlin.de>
--              Dr. Gerhard Groeger <groeger@ikg.uni-bonn.de>
--              Joerg Schmittwilken <schmittwilken@ikg.uni-bonn.de>
--              Viktor Stroh <stroh@ikg.uni-bonn.de>
--              Dr. Andreas Poth <poth@lat-lon.de>
--              Gerhard K�nig <gerhard.koenig@tu-berlin.de>
--              Claus Nagel <nagel@igg.tu-berlin.de>
--              Alexandra Stadler <stadler@igg.tu-berlin.de>
--
-- Conversion:  Laure Fraysse <Laure.fraysse@etumel.univmed.fr>
--				Felix Kunde <felix-kunde@gmx.de>
--
-- Copyright:   (c) 2007-2008  Institute for Geodesy and Geoinformation Science,
--                             Technische Universit�t Berlin, Germany
--                             http://www.igg.tu-berlin.de
--              (c) 2004-2006, Institute for Cartography and Geoinformation,
--                             Universit�t Bonn, Germany
--                             http://www.ikg.uni-bonn.de
--              (c) 2005-2006, lat/lon GmbH, Germany
--                             http://www.lat-lon.de
--
--              This skript is free software under the LGPL Version 2.1.
--              See the GNU Lesser General Public License at
--              http://www.gnu.org/copyleft/lgpl.html
--              for more details.
-------------------------------------------------------------------------------
-- About:
--
--
-------------------------------------------------------------------------------
--
-- ChangeLog:
--
-- Version | Date       | Description      | Author | Conversion
-- 2.0.0     2011-12-09   release version    LPlu	  LFra
--                                           TKol	  FKun
--                                           GGro
--                                           JSch
--                                           VStr
--                                           APot	  	
--                                           GKoe     
--                                           CNag
--                                           ASta

-- DROP TABLE "RASTER_RELIEF" CASCADE CONSTRAINT PURGE;

CREATE TABLE RASTER_RELIEF (
	ID 					SERIAL NOT NULL,
--  "LOD" NUMBER (1) NOT NULL,
	RASTERPROPERTY      RASTER
--  "RELIEF_ID" NUMBER NOT NULL,
--  "NAME" VARCHAR2 (256),
--  "TYPE" VARCHAR2 (256) 
)
;

-- SELECT public.AddGeometryColumn('raster_relief', 'extent', 3068, 'GEOMETRY', 3);

ALTER TABLE RASTER_RELIEF
ADD CONSTRAINT RASTER_RLF_PK PRIMARY KEY
(
ID
)
;