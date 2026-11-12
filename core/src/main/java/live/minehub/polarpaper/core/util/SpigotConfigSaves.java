package live.minehub.polarpaper.core.util;

import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spigotmc.SpigotConfig;

import java.io.File;
import java.util.function.Supplier;

/**
 * Stops spigot.yml being written to disk every time a polar world is created.
 * <br/><br/>
 * The Level constructor creates a SpigotWorldConfig, which reads its values and then saves the whole of spigot.yml
 * again. Worlds only read from world-settings.default (or their own section if it's already in the file), so the
 * save never changes anything.
 * While the level is constructed SpigotConfig.config is swapped for a copy that doesn't save.
 */
public final class SpigotConfigSaves {
    private static final Logger LOGGER = LoggerFactory.getLogger(SpigotConfigSaves.class);

    private static volatile boolean enabled;
    private static @Nullable YamlConfiguration source;
    private static @Nullable NoSaveConfiguration copy;

    private SpigotConfigSaves() {
    }

    public static void setEnabled(boolean enabled) {
        SpigotConfigSaves.enabled = enabled;
    }

    public static <T> T skip(Supplier<T> levelCreator) {
        if (!enabled) return levelCreator.get();

        synchronized (SpigotConfigSaves.class) {
            YamlConfiguration current = SpigotConfig.config;
            if (current == null || current instanceof NoSaveConfiguration) return levelCreator.get();

            NoSaveConfiguration noSave;
            try {
                noSave = copyOf(current);
            } catch (InvalidConfigurationException | RuntimeException e) {
                LOGGER.warn("Not skipping spigot config saves, failed to copy the config", e);
                enabled = false;
                return levelCreator.get();
            }

            SpigotConfig.config = noSave;
            try {
                return levelCreator.get();
            } finally {
                SpigotConfig.config = current;
            }
        }
    }

    // the copy is kept until spigot.yml is reloaded, which replaces SpigotConfig.config
    private static NoSaveConfiguration copyOf(YamlConfiguration current) throws InvalidConfigurationException {
        NoSaveConfiguration cached = copy;
        if (cached != null && source == current) return cached;

        NoSaveConfiguration created = new NoSaveConfiguration();
        created.loadFromString(current.saveToString());
        Configuration defaults = current.getDefaults();
        if (defaults != null) created.setDefaults(defaults);
        created.options().copyDefaults(true);

        source = current;
        copy = created;
        return created;
    }

    private static final class NoSaveConfiguration extends YamlConfiguration {
        @Override
        public void save(File file) {
        }
    }
}
