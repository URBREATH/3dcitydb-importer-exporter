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
package org.citydb.operation.exporter.controller;

import org.citydb.operation.common.cache.CacheTableManager;
import org.citydb.operation.common.cache.IdCacheManager;
import org.citydb.operation.common.cache.IdCacheType;
import org.citydb.operation.common.xlink.DBXlink;
import org.citydb.operation.exporter.CityGMLExportException;
import org.citydb.operation.exporter.cache.GeometryGmlIdCache;
import org.citydb.operation.exporter.cache.ObjectGmlIdCache;
import org.citydb.operation.exporter.concurrent.DBExportWorkerFactory;
import org.citydb.operation.exporter.concurrent.DBExportXlinkWorkerFactory;
import org.citydb.operation.exporter.database.content.DBSplitter;
import org.citydb.operation.exporter.database.content.DBSplittingResult;
import org.citydb.operation.exporter.util.InternalConfig;
import org.citydb.operation.exporter.writer.FeatureWriteException;
import org.citydb.operation.exporter.writer.FeatureWriter;
import org.citydb.operation.exporter.writer.FeatureWriterFactory;
import org.citydb.operation.exporter.writer.FeatureWriterFactoryBuilder;
import org.citydb.concurrent.PoolSizeAdaptationStrategy;
import org.citydb.concurrent.WorkerPool;
import org.citydb.config.Config;
import org.citydb.config.exception.ErrorCode;
import org.citydb.config.i18n.Language;
import org.citydb.config.project.database.DatabaseSrs;
import org.citydb.config.project.database.Workspace;
import org.citydb.config.project.exporter.OutputFormat;
import org.citydb.config.project.exporter.SimpleTilingOptions;
import org.citydb.config.project.exporter.TileNameSuffixMode;
import org.citydb.config.project.exporter.TileSuffixMode;
import org.citydb.config.project.exporter.XLink;
import org.citydb.database.adapter.AbstractDatabaseAdapter;
import org.citydb.database.adapter.IndexStatusInfo.IndexType;
import org.citydb.database.connection.DatabaseConnectionPool;
import org.citydb.database.schema.mapping.SchemaMapping;
import org.citydb.event.Event;
import org.citydb.event.EventDispatcher;
import org.citydb.event.EventHandler;
import org.citydb.event.global.CounterEvent;
import org.citydb.event.global.CounterType;
import org.citydb.event.global.EventType;
import org.citydb.event.global.GeometryCounterEvent;
import org.citydb.event.global.InterruptEvent;
import org.citydb.event.global.ObjectCounterEvent;
import org.citydb.event.global.StatusDialogMessage;
import org.citydb.event.global.StatusDialogProgressBar;
import org.citydb.event.global.StatusDialogTitle;
import org.citydb.file.FileType;
import org.citydb.file.OutputFile;
import org.citydb.file.output.OutputFileFactory;
import org.citydb.log.Logger;
import org.citydb.plugin.PluginManager;
import org.citydb.plugin.extension.export.CityGMLExportExtension;
import org.citydb.plugin.extension.export.MetadataProvider;
import org.citydb.query.Query;
import org.citydb.query.builder.QueryBuildException;
import org.citydb.query.builder.config.ConfigQueryBuilder;
import org.citydb.query.filter.FilterException;
import org.citydb.query.filter.selection.Predicate;
import org.citydb.query.filter.selection.SelectionFilter;
import org.citydb.query.filter.selection.operator.logical.LogicalOperationFactory;
import org.citydb.query.filter.tiling.Tile;
import org.citydb.query.filter.tiling.Tiling;
import org.citydb.registry.ObjectRegistry;
import org.citydb.util.CoreConstants;
import org.citydb.util.Util;
import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.model.citygml.cityobjectgroup.CityObjectGroup;
import org.citygml4j.model.gml.GMLClass;
import org.citygml4j.model.module.citygml.CityGMLModuleType;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;

public class Exporter implements EventHandler {
    private final Logger log = Logger.getInstance();
    private final CityGMLBuilder cityGMLBuilder;
    private final AbstractDatabaseAdapter databaseAdapter;
    private final SchemaMapping schemaMapping;
    private final Config config;
    private final EventDispatcher eventDispatcher;
    private final AtomicBoolean isInterrupted = new AtomicBoolean(false);

    private final Map<Integer, Long> objectCounter;
    private final Map<GMLClass, Long> geometryCounter;
    private final Map<Integer, Long> totalObjectCounter;
    private final Map<GMLClass, Long> totalGeometryCounter;

    private DBSplitter dbSplitter;
    private WorkerPool<DBSplittingResult> dbWorkerPool;
    private WorkerPool<DBXlink> xlinkExporterPool;
    private boolean useTiling;

    private volatile boolean shouldRun = true;
    private CityGMLExportException exception;

	public Exporter() {
        cityGMLBuilder = ObjectRegistry.getInstance().getCityGMLBuilder();
        schemaMapping = ObjectRegistry.getInstance().getSchemaMapping();
        config = ObjectRegistry.getInstance().getConfig();
        eventDispatcher = ObjectRegistry.getInstance().getEventDispatcher();
        databaseAdapter = DatabaseConnectionPool.getInstance().getActiveDatabaseAdapter();

        objectCounter = new HashMap<>();
        geometryCounter = new EnumMap<>(GMLClass.class);
        totalObjectCounter = new HashMap<>();
        totalGeometryCounter = new EnumMap<>(GMLClass.class);
    }

    public boolean doExport(Path outputFile) throws CityGMLExportException {
        if (outputFile == null || outputFile.getFileName() == null) {
            throw new CityGMLExportException("The output file '" + outputFile + "' is invalid.");
        }

        eventDispatcher.addEventHandler(EventType.OBJECT_COUNTER, this);
        eventDispatcher.addEventHandler(EventType.GEOMETRY_COUNTER, this);
        eventDispatcher.addEventHandler(EventType.INTERRUPT, this);

        try {
            return process(outputFile);
        } finally {
            eventDispatcher.removeEventHandler(this);
        }
    }

    private boolean process(Path outputFile) throws CityGMLExportException {
        InternalConfig internalConfig = new InternalConfig();

        // set output format and format-specific options
        OutputFormat outputFormat = OutputFileFactory.getOutputFormat(outputFile, config);
        setOutputFormatOptions(outputFormat, internalConfig);

        // log workspace
        if (databaseAdapter.hasVersioningSupport() && databaseAdapter.getConnectionDetails().isSetWorkspace()) {
            Workspace workspace = databaseAdapter.getConnectionDetails().getWorkspace();
            if (!databaseAdapter.getWorkspaceManager().equalsDefaultWorkspaceName(workspace.getName())) {
                log.info("Exporting from workspace " + databaseAdapter.getConnectionDetails().getWorkspace() + ".");
            }
        }

        // build query from filter settings
        Query query;
        try {
            ConfigQueryBuilder queryBuilder = new ConfigQueryBuilder(schemaMapping, databaseAdapter);
            query = config.getExportConfig().isUseSimpleQuery() ?
                    queryBuilder.buildQuery(config.getExportConfig().getSimpleQuery(), config.getNamespaceFilter()) :
                    queryBuilder.buildQuery(config.getExportConfig().getQuery(), config.getNamespaceFilter());
        } catch (QueryBuildException e) {
            throw new CityGMLExportException("Failed to build the export query expression.", e);
        }

        // create feature writer factory
        FeatureWriterFactory writerFactory;
        try {
            writerFactory = FeatureWriterFactoryBuilder.buildFactory(outputFormat, query, schemaMapping, config);
        } catch (FeatureWriteException e) {
            throw new CityGMLExportException("Failed to build the feature writer factory.", e);
        }

        // get metadata provider
        MetadataProvider metadataProvider = null;
        if (config.getExportConfig().isSetMetadataProvider()) {
            for (CityGMLExportExtension plugin : PluginManager.getInstance().getExternalPlugins(CityGMLExportExtension.class)) {
                if (plugin instanceof MetadataProvider
                        && plugin.getClass().getCanonicalName().equals(config.getExportConfig().getMetadataProvider())) {
                    metadataProvider = (MetadataProvider) plugin;
                    break;
                }
            }

            if (metadataProvider == null) {
                throw new CityGMLExportException("Failed to load metadata provider '" + config.getExportConfig().getMetadataProvider() + "'.");
            }
        }

        // set target reference system for export
        DatabaseSrs targetSrs = query.getTargetSrs();
        internalConfig.setTransformCoordinates(targetSrs.isSupported()
                && targetSrs.getSrid() != databaseAdapter.getConnectionMetaData().getReferenceSystem().getSrid());

        if (internalConfig.isTransformCoordinates()) {
            log.info("Transforming geometry representation to reference system '" + targetSrs.getDescription() + "' (SRID: " + targetSrs.getSrid() + ").");
            if (!targetSrs.is3D() && !databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D()) {
                log.warn("Transformation is NOT applied to height reference system.");
            } else if (targetSrs.is3D() != databaseAdapter.getConnectionMetaData().getReferenceSystem().is3D()) {
                throw new CityGMLExportException("Dimensionality of reference system for geometry transformation does not match.");
            }
        }

        // check and log index status
        try {
            if ((query.isSetTiling() || (query.isSetSelection() && query.getSelection().containsSpatialOperators()))
                    && !databaseAdapter.getUtil().isIndexEnabled("CITYOBJECT", "ENVELOPE")) {
                throw new CityGMLExportException(ErrorCode.SPATIAL_INDEXES_NOT_ACTIVATED, "Spatial indexes are not activated.");
            }

            for (IndexType type : IndexType.values()) {
            	databaseAdapter.getUtil().getIndexStatus(type).printStatusToConsole();
			}
        } catch (SQLException e) {
            throw new CityGMLExportException("Database error while querying index status.", e);
        }

        // check whether database contains global appearances and set internal flag
        try {
            internalConfig.setExportGlobalAppearances(config.getExportConfig().getAppearances().isSetExportAppearance()
					&& databaseAdapter.getUtil().containsGlobalAppearances());
        } catch (SQLException e) {
            throw new CityGMLExportException("Database error while querying the number of global appearances.", e);
        }

        // cache gml:ids of city objects in case we have to export groups
        internalConfig.setRegisterGmlIdInCache(!config.getExportConfig().getCityObjectGroup().isExportMemberAsXLinks()
                && query.getFeatureTypeFilter().containsFeatureType(
                		schemaMapping.getFeatureType(query.getTargetVersion()
								.getCityGMLModule(CityGMLModuleType.CITY_OBJECT_GROUP)
								.getFeatureName(CityObjectGroup.class))));

        // tiling
        Tiling tiling = query.getTiling();
        SimpleTilingOptions tilingOptions = null;
        Predicate predicate = null;
        useTiling = query.isSetTiling();
        int rows = useTiling ? tiling.getRows() : 1;
        int columns = useTiling ? tiling.getColumns() : 1;

        if (useTiling) {
            try {
                // transform tiling extent to database srs
                tiling.transformExtent(databaseAdapter.getConnectionMetaData().getReferenceSystem(), databaseAdapter);
                predicate = query.isSetSelection() ? query.getSelection().getPredicate() : null;
                tilingOptions = tiling.getTilingOptions() instanceof SimpleTilingOptions ? (SimpleTilingOptions) tiling.getTilingOptions() : new SimpleTilingOptions();
            } catch (FilterException e) {
                throw new CityGMLExportException("Failed to transform tiling extent.", e);
            }
        } else if (outputFormat == OutputFormat.CITYJSON) {
            // log warning if CityJSON is used without tiling
            log.warn("To avoid memory issues, a tiled export should be used for CityJSON.");
        }

        // create output file factory
        OutputFileFactory fileFactory = new OutputFileFactory(config, eventDispatcher);

        // process export folder for texture files
        String textureFolder = null;
        boolean textureFolderIsAbsolute = false;
        boolean exportAppearance = config.getExportConfig().getAppearances().isSetExportAppearance();

        if (exportAppearance) {
            textureFolder = config.getExportConfig().getAppearances().getTexturePath().getPath();
            if (textureFolder == null || textureFolder.isEmpty()) {
            	textureFolder = "appearance";
			}

            textureFolderIsAbsolute = new File(textureFolder).isAbsolute();
            if (!textureFolderIsAbsolute) {
            	textureFolder = textureFolder.replace("\\", "/");
			}

            if (textureFolderIsAbsolute) {
                try {
                    Path path = Paths.get(textureFolder).toAbsolutePath().normalize();
                    textureFolder = path.toString();
                    if (!Files.isDirectory(path)) {
                        Files.createDirectories(path);
                        log.info("Created texture files folder '" + textureFolder + "'.");
                    }
                } catch (IOException | InvalidPathException e) {
                    throw new CityGMLExportException("Failed to create texture files folder '" + textureFolder + "'.", e);
                }
            }

            internalConfig.setExportTextureURI(textureFolder);

            // check for unique texture filenames when exporting as archive
            if (!config.getExportConfig().getAppearances().isSetUniqueTextureFileNames()
                    && OutputFileFactory.getFileType(outputFile.getFileName()) == FileType.ARCHIVE) {
                log.warn("Using unique texture filenames because of writing to an archive file.");
                config.getExportConfig().getAppearances().setUniqueTextureFileNames(true);
            }
        }

        int remainingTiles = rows * columns;
        long start = System.currentTimeMillis();

        for (int row = 0; shouldRun && row < rows; row++) {
            for (int column = 0; shouldRun && column < columns; column++) {
                String fileName = outputFile.getFileName().toString();
                Path folder = outputFile.getParent();
                if (folder == null)
                    folder = Paths.get("").toAbsolutePath().normalize();

                if (useTiling) {
                    Tile tile;
                    try {
                        tile = tiling.getTileAt(row, column);
                        tiling.setActiveTile(tile);

                        Predicate bboxFilter = tile.getFilterPredicate(databaseAdapter);
						query.setSelection(predicate != null ?
								new SelectionFilter(LogicalOperationFactory.AND(predicate, bboxFilter)) :
								new SelectionFilter(bboxFilter));
                    } catch (FilterException e) {
                        throw new CityGMLExportException("Failed to get tile at [" + row + "," + column + "].", e);
                    }

                    // create suffix for folderName and fileName
                    TileSuffixMode suffixMode = tilingOptions.getTilePathSuffix();
                    double minX = tile.getExtent().getLowerCorner().getX();
                    double minY = tile.getExtent().getLowerCorner().getY();
                    double maxX = tile.getExtent().getUpperCorner().getX();
                    double maxY = tile.getExtent().getUpperCorner().getY();

					String suffix;
                    switch (suffixMode) {
                        case XMIN_YMIN:
                            suffix = String.valueOf(minX) + '_' + minY;
                            break;
                        case XMAX_YMIN:
                            suffix = String.valueOf(maxX) + '_' + minY;
                            break;
                        case XMIN_YMAX:
                            suffix = String.valueOf(minX) + '_' + maxY;
                            break;
                        case XMAX_YMAX:
                            suffix = String.valueOf(maxX) + '_' + maxY;
                            break;
                        case XMIN_YMIN_XMAX_YMAX:
                            suffix = String.valueOf(minX) + '_' + minY + '_' + maxX + '_' + maxY;
                            break;
                        default:
                            suffix = String.valueOf(row) + '_' + column;
                    }

                    folder = folder.resolve(tilingOptions.getTilePath() + '_' + suffix);
                    if (tilingOptions.getTileNameSuffix() == TileNameSuffixMode.SAME_AS_PATH) {
                        int index = fileName.indexOf('.');
                        fileName = index > 0 ?
                                fileName.substring(0, index) + '_' + suffix + fileName.substring(index) :
                                fileName + '_' + suffix;
                    }
                }

                CacheTableManager cacheTableManager = null;
                IdCacheManager idCacheManager = null;
                FeatureWriter writer = null;
                OutputFile file = null;

                try {
                    eventDispatcher.triggerEvent(new StatusDialogMessage(Language.I18N.getString("export.dialog.cityObj.msg"), this));
                    eventDispatcher.triggerEvent(new StatusDialogTitle(fileName, this));
                    eventDispatcher.triggerEvent(new CounterEvent(CounterType.REMAINING_TILES, --remainingTiles, this));

                    try {
                        file = fileFactory.createOutputFile(folder.resolve(fileName), outputFormat);
                        internalConfig.setOutputFile(file);
                    } catch (IOException e) {
                        throw new CityGMLExportException("Failed to create output file '" + folder.resolve(fileName) + "'.", e);
                    }

                    // create relative folder for texture files
                    if (exportAppearance && !textureFolderIsAbsolute &&
                            (file.getType() == FileType.ARCHIVE || !Files.isDirectory(Paths.get(file.resolve(textureFolder))))) {
                        try {
                            file.createDirectories(textureFolder);
                            log.info("Created texture files folder '" + textureFolder + "'.");
                        } catch (IOException e) {
                            throw new CityGMLExportException("Failed to create texture files folder '" + textureFolder + "'.", e);
                        }
                    }

                    // create output writer
                    try {
                        writer = writerFactory.createFeatureWriter(file.openStream());
                    } catch (FeatureWriteException | IOException e) {
                        throw new CityGMLExportException("Failed to open file '" + file.getFile() + "' for writing.", e);
                    }

                    // create instance of temp table manager
                    try {
                        cacheTableManager = new CacheTableManager(
                                config.getExportConfig().getResources().getThreadPool().getMaxThreads(),
                                config);
                    } catch (SQLException | IOException e) {
                        throw new CityGMLExportException("Failed to initialize internal cache manager.", e);
                    }

                    // create instance of gml:id lookup server manager...
                    idCacheManager = new IdCacheManager();

                    // ...and start servers
                    try {
                        idCacheManager.initCache(
                                IdCacheType.GEOMETRY,
                                new GeometryGmlIdCache(cacheTableManager,
                                        config.getExportConfig().getResources().getIdCache().getGeometry().getPartitions(),
                                        config.getDatabaseConfig().getImportBatching().getGmlIdCacheBatchSize()),
                                config.getExportConfig().getResources().getIdCache().getGeometry().getCacheSize(),
                                config.getExportConfig().getResources().getIdCache().getGeometry().getPageFactor(),
                                config.getExportConfig().getResources().getThreadPool().getMaxThreads());

                        idCacheManager.initCache(
                                IdCacheType.OBJECT,
                                new ObjectGmlIdCache(cacheTableManager,
                                        config.getExportConfig().getResources().getIdCache().getFeature().getPartitions(),
                                        config.getDatabaseConfig().getImportBatching().getGmlIdCacheBatchSize()),
                                config.getExportConfig().getResources().getIdCache().getFeature().getCacheSize(),
                                config.getExportConfig().getResources().getIdCache().getFeature().getPageFactor(),
                                config.getExportConfig().getResources().getThreadPool().getMaxThreads());
                    } catch (SQLException e) {
                        throw new CityGMLExportException("Failed to initialize internal gml:id caches.", e);
                    }

                    // create worker pools
                    // here we have an open issue: queue sizes are fix...
                    xlinkExporterPool = new WorkerPool<>(
                            "xlink_exporter_pool",
                            1,
                            Math.max(1, config.getExportConfig().getResources().getThreadPool().getMaxThreads() / 2),
                            PoolSizeAdaptationStrategy.AGGRESSIVE,
                            new DBExportXlinkWorkerFactory(internalConfig, config, eventDispatcher),
                            300,
                            false);

                    dbWorkerPool = new WorkerPool<>(
                            "db_exporter_pool",
                            config.getExportConfig().getResources().getThreadPool().getMinThreads(),
                            config.getExportConfig().getResources().getThreadPool().getMaxThreads(),
                            PoolSizeAdaptationStrategy.AGGRESSIVE,
                            new DBExportWorkerFactory(
                                    schemaMapping,
                                    cityGMLBuilder,
                                    writer,
                                    xlinkExporterPool,
                                    idCacheManager,
                                    cacheTableManager,
                                    query,
                                    internalConfig,
                                    config,
                                    eventDispatcher),
                            300,
                            false);

                    // prestart pool workers
                    xlinkExporterPool.prestartCoreWorkers();
                    dbWorkerPool.prestartCoreWorkers();

                    // fail if we could not start a single import worker
                    if (dbWorkerPool.getPoolSize() == 0) {
                    	throw new CityGMLExportException("Failed to start database export worker pool. Check the database connection pool settings.");
					}

                    log.info("Exporting to file: " + file.getFile());

                    // get database splitter and start query
                    try {
                        dbSplitter = new DBSplitter(
                                writer,
                                schemaMapping,
                                dbWorkerPool,
                                query,
                                idCacheManager.getCache(IdCacheType.OBJECT),
                                cacheTableManager,
                                eventDispatcher,
                                internalConfig,
                                config);

                        if (shouldRun) {
                            dbSplitter.setMetadataProvider(metadataProvider);
                            dbSplitter.setCalculateNumberMatched(CoreConstants.IS_GUI_MODE);
                            dbSplitter.startQuery();
                        }
                    } catch (SQLException | QueryBuildException | FilterException e) {
                        throw new CityGMLExportException("Failed to query the database.", e);
                    } catch (FeatureWriteException e) {
                        throw new CityGMLExportException("Failed to write to output file.", e);
                    }

                    try {
                        dbWorkerPool.shutdownAndWait();
                        xlinkExporterPool.shutdownAndWait();
                    } catch (InterruptedException e) {
                        throw new CityGMLExportException("Failed to shutdown worker pools.", e);
                    }

                    eventDispatcher.triggerEvent(new StatusDialogProgressBar(true, this));
                    eventDispatcher.triggerEvent(new StatusDialogMessage(Language.I18N.getString("export.dialog.finish.msg"), this));
                } catch (CityGMLExportException e) {
                    throw e;
                } catch (Throwable e) {
                    throw new CityGMLExportException("An unexpected error occurred.", e);
                } finally {
                    // close writer before closing output file
                    if (writer != null) {
                        try {
                            writer.close();
                        } catch (FeatureWriteException e) {
                            setException("Failed to close output writer.", e);
                            shouldRun = false;
                        }
                    }

                    if (file != null) {
                        try {
                            file.close();
                        } catch (IOException e) {
                            setException("Failed to close output file.", e);
                            shouldRun = false;
                        }
                    }

                    // clean up
                    if (xlinkExporterPool != null && !xlinkExporterPool.isTerminated()) {
                    	xlinkExporterPool.shutdownNow();
					}

                    if (dbWorkerPool != null && !dbWorkerPool.isTerminated()) {
                    	dbWorkerPool.shutdownNow();
					}

                    try {
                        eventDispatcher.flushEvents();
                    } catch (InterruptedException e) {
                        //
                    }

                    if (idCacheManager != null) {
                        try {
                            idCacheManager.shutdownAll();
                        } catch (SQLException e) {
                            setException("Failed to clean the gml:id caches.", e);
                            shouldRun = false;
                        }
                    }

                    if (cacheTableManager != null) {
                        try {
                            log.info("Cleaning temporary cache.");
                            cacheTableManager.dropAll();
                        } catch (SQLException e) {
                            setException("Failed to clean the temporary cache.", e);
                            shouldRun = false;
                        }
                    }
                }

                // show exported features
                if (!objectCounter.isEmpty()) {
                    log.info("Exported city objects:");
                    Map<String, Long> typeNames = Util.mapObjectCounter(objectCounter, schemaMapping);
                    typeNames.keySet().forEach(object -> log.info(object + ": " + typeNames.get(object)));
                }

                // show processed geometries
                if (!geometryCounter.isEmpty()) {
                	log.info("Processed geometry objects: " + geometryCounter.values().stream().reduce(0L, Long::sum));
				}

                objectCounter.clear();
                geometryCounter.clear();
            }
        }

        // show totally exported features
        if (useTiling && (rows > 1 || columns > 1)) {
            if (!totalObjectCounter.isEmpty()) {
                log.info("Total exported CityGML features:");
                Map<String, Long> typeNames = Util.mapObjectCounter(totalObjectCounter, schemaMapping);
                typeNames.keySet().forEach(object -> log.info(object + ": " + typeNames.get(object)));
            }

            if (!totalGeometryCounter.isEmpty()) {
            	log.info("Total processed objects: " + totalGeometryCounter.values().stream().reduce(0L, Long::sum));
			}
        }

        if (shouldRun) {
        	log.info("Total export time: " + Util.formatElapsedTime(System.currentTimeMillis() - start) + ".");
		} else if (exception != null) {
            throw exception;
        }

        return shouldRun;
    }

    private void setOutputFormatOptions(OutputFormat outputFormat, InternalConfig internalConfig) {
	    internalConfig.setOutputFormat(outputFormat);

	    if (outputFormat == OutputFormat.CITYJSON) {
	        internalConfig.setExportFeatureReferences(false);
	        internalConfig.setExportGeometryReferences(true);
        } else {
            XLink xlinkOptions = config.getExportConfig().getCityGMLOptions().getXlink();
	        internalConfig.setExportFeatureReferences(xlinkOptions.getFeature().isModeXLink());
	        internalConfig.setExportGeometryReferences(xlinkOptions.getGeometry().isModeXLink());
        }
    }

    private void setException(String message, Throwable cause) {
	    if (exception == null) {
	        exception = new CityGMLExportException(message, cause);
        }
    }

    @Override
    public void handleEvent(Event e) throws Exception {
        if (e.getEventType() == EventType.OBJECT_COUNTER) {
            Map<Integer, Long> counter = ((ObjectCounterEvent) e).getCounter();
            for (Entry<Integer, Long> entry : counter.entrySet()) {
                Long tmp = objectCounter.get(entry.getKey());
                objectCounter.put(entry.getKey(), tmp == null ? entry.getValue() : tmp + entry.getValue());
                if (useTiling) {
                    tmp = totalObjectCounter.get(entry.getKey());
                    totalObjectCounter.put(entry.getKey(), tmp == null ? entry.getValue() : tmp + entry.getValue());
                }
            }
        } else if (e.getEventType() == EventType.GEOMETRY_COUNTER) {
            Map<GMLClass, Long> counter = ((GeometryCounterEvent) e).getCounter();
            for (Entry<GMLClass, Long> entry : counter.entrySet()) {
                Long tmp = geometryCounter.get(entry.getKey());
                geometryCounter.put(entry.getKey(), tmp == null ? entry.getValue() : tmp + entry.getValue());
                if (useTiling) {
                    tmp = totalGeometryCounter.get(entry.getKey());
                    totalGeometryCounter.put(entry.getKey(), tmp == null ? entry.getValue() : tmp + entry.getValue());
                }
            }
        } else if (e.getEventType() == EventType.INTERRUPT) {
            if (isInterrupted.compareAndSet(false, true)) {
                shouldRun = false;
                InterruptEvent event = (InterruptEvent) e;

                log.log(event.getLogLevelType(), event.getLogMessage());
                if (event.getCause() != null) {
                    setException("Aborting export due to errors.", event.getCause());
                }

                if (dbSplitter != null) {
                	dbSplitter.shutdown();
				}

                if (dbWorkerPool != null) {
                	dbWorkerPool.drainWorkQueue();
				}

                if (xlinkExporterPool != null) {
                	xlinkExporterPool.drainWorkQueue();
				}
            }
        }
    }
}