/*
 * 3D City Database - The Open Source CityGML Database
 * https://www.3dcitydb.org/
 *
 * Copyright 2013 - 2021
 * Chair of Geoinformatics
 * Technical University of Munich, Germany
 * https://www.lrg.tum.de/gis/
 *
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 *
 * Virtual City Systems, Berlin <https://vc.systems/>
 * M.O.S.S. Computer Grafik Systeme GmbH, Taufkirchen <http://www.moss.de/>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 */
package org.citydb.operation.importer.database.xlink.importer;

import java.sql.PreparedStatement;
import java.sql.SQLException;

import org.citydb.operation.common.cache.CacheTable;
import org.citydb.operation.common.xlink.DBXlinkGroupToCityObject;

public class DBXlinkImporterGroupToCityObject implements DBXlinkImporter {
	private final DBXlinkImporterManager xlinkImporterManager;
	private PreparedStatement psXlink;
	private int batchCounter;

	public DBXlinkImporterGroupToCityObject(CacheTable tempTable, DBXlinkImporterManager xlinkImporterManager) throws SQLException {
		this.xlinkImporterManager = xlinkImporterManager;

		psXlink = tempTable.getConnection().prepareStatement("insert into " + tempTable.getTableName() + 
			" (GROUP_ID, GMLID, IS_PARENT, ROLE) values " +
			"(?, ?, ?, ?)");
	}
	
	public boolean insert(DBXlinkGroupToCityObject xlinkEntry) throws SQLException {
		psXlink.setLong(1, xlinkEntry.getGroupId());
		psXlink.setString(2, xlinkEntry.getGmlId());		
		psXlink.setInt(3, xlinkEntry.isParent() ? 1 : 0);		
		psXlink.setString(4, xlinkEntry.getRole());

		psXlink.addBatch();
		if (++batchCounter == xlinkImporterManager.getCacheAdapter().getMaxBatchSize())
			executeBatch();

		return true;
	}
	
	@Override
	public void executeBatch() throws SQLException {
		psXlink.executeBatch();
		batchCounter = 0;
	}

	@Override
	public void close() throws SQLException {
		psXlink.close();
	}

	@Override
	public DBXlinkImporterEnum getDBXlinkImporterType() {
		return DBXlinkImporterEnum.GROUP_TO_CITYOBJECT;
	}

}