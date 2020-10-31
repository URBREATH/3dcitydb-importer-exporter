/*
 * 3D City Database - The Open Source CityGML Database
 * http://www.3dcitydb.org/
 *
 * Copyright 2013 - 2020
 * Chair of Geoinformatics
 * Technical University of Munich, Germany
 * https://www.gis.bgu.tum.de/
 *
 * The 3D City Database is jointly developed with the following
 * cooperation partners:
 *
 * virtualcitySYSTEMS GmbH, Berlin <http://www.virtualcitysystems.de/>
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

package org.citydb.cli.options.exporter;

import org.citydb.config.project.exporter.ExportAppearance;
import org.citydb.config.project.query.QueryConfig;
import org.citydb.config.project.query.filter.selection.AbstractPredicate;
import org.citydb.config.project.query.filter.selection.SelectionFilter;
import org.citydb.config.project.query.filter.selection.id.ResourceIdOperator;
import org.citydb.config.project.query.filter.selection.logical.AndOperator;
import org.citydb.config.project.query.filter.selection.spatial.AbstractSpatialOperator;
import org.citydb.config.project.query.filter.selection.sql.SelectOperator;
import org.citydb.plugin.cli.CliOption;
import org.citydb.plugin.cli.ResourceIdOption;
import org.citydb.plugin.cli.SQLSelectOption;
import org.citydb.plugin.cli.TypeNamesOption;
import org.citydb.plugin.cli.XMLQueryOption;
import org.citydb.registry.ObjectRegistry;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

public class QueryOption implements CliOption {
    @CommandLine.ArgGroup(exclusive = false)
    private TypeNamesOption typeNamesOption;

    @CommandLine.ArgGroup
    private ResourceIdOption idOption;

    @CommandLine.ArgGroup(exclusive = false)
    private BoundingBoxOption boundingBoxOption;

    @CommandLine.ArgGroup(exclusive = false)
    private CounterOption counterOption;

    @CommandLine.ArgGroup(exclusive = false)
    private LodOption lodOption;

    @CommandLine.ArgGroup
    private AppearanceOption appearanceOption;

    @CommandLine.ArgGroup
    private SQLSelectOption sqlSelectOption;

    @CommandLine.ArgGroup
    private XMLQueryOption xmlQueryOption;

    public QueryConfig toQueryConfig() {
        if (typeNamesOption != null
                || idOption != null
                || boundingBoxOption != null
                || counterOption != null
                || lodOption != null
                || appearanceOption != null
                || sqlSelectOption != null) {
            QueryConfig queryConfig = new QueryConfig();
            List<AbstractPredicate> predicates = new ArrayList<>();

            if (typeNamesOption != null) {
                queryConfig.setFeatureTypeFilter(typeNamesOption.toFeatureTypeFilter());
            }

            if (idOption != null) {
                ResourceIdOperator idOperator = idOption.toResourceIdOperator();
                if (idOperator != null) {
                    predicates.add(idOperator);
                }
            }

            if (boundingBoxOption != null) {
                AbstractSpatialOperator spatialOperator = boundingBoxOption.toSpatialOperator();
                if (spatialOperator != null) {
                    predicates.add(spatialOperator);
                }
            }

            if (counterOption != null) {
                queryConfig.setCounterFilter(counterOption.toCounterFilter());
            }

            if (lodOption != null) {
                queryConfig.setLodFilter(lodOption.toLodFilter());
            }

            if (appearanceOption != null) {
                if (appearanceOption.isExportAppearances()) {
                    queryConfig.setAppearanceFilter(appearanceOption.toAppearanceFilter());
                } else {
                    ExportAppearance appearance = ObjectRegistry.getInstance().getConfig().getExportConfig().getAppearances();
                    appearance.setExportAppearances(false);
                }
            }

            if (sqlSelectOption != null) {
                SelectOperator selectOperator = sqlSelectOption.toSelectOperator();
                if (selectOperator != null) {
                    predicates.add(selectOperator);
                }
            }

            if (!predicates.isEmpty()) {
                AndOperator andOperator = new AndOperator();
                andOperator.setOperands(predicates);
                SelectionFilter selectionFilter = new SelectionFilter();
                selectionFilter.setPredicate(andOperator);
                queryConfig.setSelectionFilter(selectionFilter);
            }

            return queryConfig;
        } else {
            return xmlQueryOption.toQueryConfig();
        }
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (xmlQueryOption != null) {
            if (typeNamesOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --type-names and --xml-query are mutually exclusive (specify only one)");
            }

            if (idOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --ids and --xml-query are mutually exclusive (specify only one)");
            }

            if (boundingBoxOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --bbox and --xml-query are mutually exclusive (specify only one)");
            }

            if (counterOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: Counter options and --xml-query are mutually exclusive (specify only one)");
            }

            if (lodOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --lods and --xml-query are mutually exclusive (specify only one)");
            }

            if (appearanceOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: Appearance options and --xml-query are mutually exclusive (specify only one)");
            }

            if (sqlSelectOption != null) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --sql-select and --xml-query are mutually exclusive (specify only one)");
            }

            xmlQueryOption.preprocess(commandLine);
        }

        if (typeNamesOption != null) {
            typeNamesOption.preprocess(commandLine);
        }

        if (boundingBoxOption != null) {
            boundingBoxOption.preprocess(commandLine);
        }

        if (counterOption != null) {
            counterOption.preprocess(commandLine);
        }

        if (lodOption != null) {
            lodOption.preprocess(commandLine);
        }
    }
}