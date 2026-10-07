package com.ziggleflig.golf;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Path;

import com.electronwill.nightconfig.core.file.FileWatcher;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.loading.FMLConfig;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import net.neoforged.neoforge.common.ModConfigSpec;

/** World-specific, server-authoritative launch power settings. */
public final class GolfClubConfig {
    public static final double MIN_MULTIPLIER = 0.1D;
    public static final double MAX_MULTIPLIER = 5.0D;
    public static final ModConfigSpec SPEC;
    public static final Map<String, ModConfigSpec.DoubleValue> MULTIPLIERS;
    public static final String FILE_NAME = "fligcraft_golf-server.toml";
    private static final Map<MinecraftServer, GolfClubSettings> SERVERS = new ConcurrentHashMap<>();

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        Map<String, ModConfigSpec.DoubleValue> values = new LinkedHashMap<>();
        builder.push("clubs");
        values.put("global", builder
            .comment("Launch power multiplier applied to every club, including the putter.",
                "Multiplies the individual club setting. Power is not an exact distance multiplier.",
                "Changes apply to subsequent shots without restarting the world.")
            .defineInRange("globalPowerMultiplier", 1.0D, MIN_MULTIPLIER, MAX_MULTIPLIER));
        for (String club : new String[] { "driver", "hybrid", "iron", "pitching_wedge", "sand_wedge", "putter" }) {
            String key = switch (club) {
                case "pitching_wedge" -> "pitchingWedgePowerMultiplier";
                case "sand_wedge" -> "sandWedgePowerMultiplier";
                default -> club + "PowerMultiplier";
            };
            values.put(club, builder
                .comment("Launch power multiplier for " + club + "; combined with globalPowerMultiplier.")
                .defineInRange(key, 1.0D, MIN_MULTIPLIER, MAX_MULTIPLIER));
        }
        builder.pop();
        SPEC = builder.build();
        MULTIPLIERS = Collections.unmodifiableMap(values);
    }

    public static GolfClubSettings forServer(MinecraftServer server) {
        return SERVERS.computeIfAbsent(server, GolfClubConfig::loadForServer);
    }

    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        forServer(event.getServer());
    }

    private static GolfClubSettings loadForServer(MinecraftServer server) {
        Path path = server.getWorldPath(new LevelResource("serverconfig")).resolve(FILE_NAME);
        GolfClubSettings settings = new GolfClubSettings(path);
        if (!FMLConfig.getBoolConfigValue(FMLConfig.ConfigValue.DISABLE_CONFIG_WATCHER)) {
            FileWatcher.defaultInstance().addWatch(path, () -> server.execute(() -> {
                // Ignore reloads queued for a server that has since stopped.
                if (SERVERS.get(server) == settings) {
                    try {
                        settings.reload();
                    } catch (RuntimeException exception) {
                        GolfMod.LOGGER.error("Could not reload club settings; keeping previous multipliers", exception);
                    }
                }
            }));
        }
        return settings;
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        GolfClubSettings settings = SERVERS.remove(event.getServer());
        if (settings != null) {
            FileWatcher.defaultInstance().removeWatch(settings.path());
            settings.close();
        }
    }

    private GolfClubConfig() {
    }
}
