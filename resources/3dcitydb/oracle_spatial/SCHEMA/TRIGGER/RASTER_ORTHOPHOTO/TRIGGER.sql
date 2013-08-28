-- TRIGGER.sql
--
-- Authors:     Prof. Dr. Thomas H. Kolbe <thomas.kolbe@tum.de>
--              Gerhard König <gerhard.koenig@tu-berlin.de>
--              Claus Nagel <cnagel@virtualcitysystems.de>
--              Alexandra Stadler <stadler@igg.tu-berlin.de>
--
-- Copyright:   (c) 2007-2008  Institute for Geodesy and Geoinformation Science,
--                             Technische Universit�t Berlin, Germany
--                             http://www.igg.tu-berlin.de
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
-- Version | Date       | Description                               | Author
-- 2.0.0     2007-11-23   release version                             TKol
--                                                                    GKoe
--                                                                    CNag
--
call sdo_geor_utl.createDMLTrigger('RASTER_RELIEF','RASTERPROPERTY');
call sdo_geor_utl.createDMLTrigger('RASTER_RELIEF_IMP','RASTERPROPERTY');

call sdo_geor_utl.createDMLTrigger('ORTHOPHOTO','ORTHOPHOTOPROPERTY');
call sdo_geor_utl.createDMLTrigger('ORTHOPHOTO_IMP','ORTHOPHOTOPROPERTY');

--@@RASTER_RELIEF_RDT_ID_TRIGGER.sql
--@@RASTER_RELIEF_RDT_IMP_ID_TRIGGER.sql

--@@ORTHOPHOTO_RDT_ID_TRIGGER.sql
--@@ORTHOPHOTO_RDT_IMP_ID_TRIGGER.sql