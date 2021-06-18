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
package org.citydb.core.plugin;

import org.citydb.config.Config;
import org.citydb.config.project.plugin.PluginConfig;
import org.citydb.core.plugin.extension.Extension;
import org.citydb.core.plugin.extension.config.ConfigExtension;
import org.citydb.core.plugin.internal.InternalPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

public class PluginManager {
    private static PluginManager instance;
    private final List<InternalPlugin> internalPlugins;
    private final List<Plugin> externalPlugins;
    private final List<CliCommand> commands;

    private PluginManager() {
        internalPlugins = new ArrayList<>();
        externalPlugins = new ArrayList<>();
        commands = new ArrayList<>();
    }

    public static synchronized PluginManager getInstance() {
        if (instance == null)
            instance = new PluginManager();

        return instance;
    }

    public void loadPlugins(ClassLoader loader) {
        ServiceLoader<Plugin> pluginLoader = ServiceLoader.load(Plugin.class, loader);
        for (Plugin plugin : pluginLoader)
            registerExternalPlugin(plugin);
    }

    public void registerInternalPlugin(InternalPlugin plugin) {
        for (Plugin candidate : internalPlugins) {
            if (candidate.getClass() == plugin.getClass())
                return;
        }

        internalPlugins.add(plugin);
    }

    public void registerExternalPlugin(Plugin plugin) {
        for (Plugin candidate : externalPlugins) {
            if (candidate.getClass() == plugin.getClass())
                return;
        }

        externalPlugins.add(plugin);
    }

    public List<InternalPlugin> getInternalPlugins() {
        return internalPlugins;
    }

    public List<Plugin> getExternalPlugins() {
        return externalPlugins;
    }

    public <T extends InternalPlugin> T getInternalPlugin(Class<T> type) {
        for (InternalPlugin plugin : internalPlugins)
            if (type.isInstance(plugin))
                return type.cast(plugin);

        return null;
    }

    public <T extends Extension> List<T> getExternalPlugins(Class<T> type) {
        List<T> plugins = new ArrayList<>();
        for (Plugin plugin : externalPlugins) {
            if (type.isAssignableFrom(plugin.getClass()))
                plugins.add(type.cast(plugin));
        }

        return plugins;
    }

    public List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>(externalPlugins);
        plugins.addAll(internalPlugins);

        return plugins;
    }

    public void loadCliCommands(ClassLoader loader) {
        ServiceLoader<CliCommand> commandLoader = ServiceLoader.load(CliCommand.class, loader);
        for (CliCommand command : commandLoader)
            registerCliCommand(command);
    }

    public void registerCliCommand(CliCommand command) {
        for (CliCommand candidate : commands) {
            if (candidate.getClass() == command.getClass())
                return;
        }

        commands.add(command);
    }

    public List<CliCommand> getCliCommands() {
        return commands;
    }

    @SuppressWarnings("unchecked")
    public <T extends PluginConfig> Class<T> getConfigClass(ConfigExtension<T> plugin) throws PluginException {
        try {
            return (Class<T>) plugin.getClass().getMethod("getConfig").getReturnType();
        } catch (Exception e) {
            throw new PluginException("Failed to determine config type of plugin " + plugin.getClass().getName() + ".", e);
        }
    }

    public <T extends PluginConfig> void propagatePluginConfig(ConfigExtension<T> plugin, Config config) throws PluginException {
        Class<T> type = getConfigClass(plugin);
        T pluginConfig = config.getPluginConfig(type);

        if (pluginConfig == null) {
            try {
                pluginConfig = type.getDeclaredConstructor().newInstance();
                config.registerPluginConfig(pluginConfig);
            } catch (Exception e) {
                throw new PluginException("Failed to invoke default constructor of " + type.getName() + ".", e);
            }
        }

        plugin.configLoaded(pluginConfig);
    }
}