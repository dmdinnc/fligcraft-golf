package com.ziggleflig.golf;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;

/** A single world's settings. Publish new values only after a successful load/save. */
public final class GolfClubSettings implements AutoCloseable {
    private final Path path;
    private final CommentedFileConfig file;
    private volatile Map<String, Double> multipliers;

    public GolfClubSettings(Path path) {
        this.path = path;
        try {
            Files.createDirectories(path.getParent());
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        file = CommentedFileConfig.builder(path).sync().build();
        try {
            reload();
        } catch (RuntimeException exception) {
            file.close();
            throw exception;
        }
    }

    public Path path() {
        return path;
    }

    public Map<String, Double> multipliers() {
        return multipliers;
    }

    public double effectiveMultiplier(String club) {
        Map<String, Double> values = multipliers;
        return values.get("global") * values.getOrDefault(club, 1.0D);
    }

    public synchronized void reload() {
        file.load();
        if (!GolfClubConfig.SPEC.isCorrect(file)) {
            GolfClubConfig.SPEC.correct(file);
            file.save();
        }
        Map<String, Double> values = new LinkedHashMap<>();
        GolfClubConfig.MULTIPLIERS.forEach((name, value) ->
            values.put(name, ((Number) file.get(value.getPath())).doubleValue()));
        multipliers = Collections.unmodifiableMap(values);
    }

    public synchronized void set(String name, double multiplier) {
        if ((!name.equals("all") && !GolfClubConfig.MULTIPLIERS.containsKey(name))
                || !Double.isFinite(multiplier) || multiplier < GolfClubConfig.MIN_MULTIPLIER
                || multiplier > GolfClubConfig.MAX_MULTIPLIER) {
            throw new IllegalArgumentException("Invalid club or multiplier");
        }
        // Re-read first so commands do not overwrite valid manual edits waiting for the watcher.
        reload();
        Map<String, Double> next = new LinkedHashMap<>(multipliers);
        GolfClubConfig.MULTIPLIERS.forEach((key, value) -> {
            if (name.equals("all") || name.equals(key)) {
                file.set(value.getPath(), multiplier);
                next.put(key, multiplier);
            }
        });
        try {
            file.save();
        } catch (RuntimeException exception) {
            GolfClubConfig.MULTIPLIERS.forEach((key, value) -> file.set(value.getPath(), multipliers.get(key)));
            throw exception;
        }
        multipliers = Collections.unmodifiableMap(next);
    }

    @Override
    public synchronized void close() {
        file.close();
    }
}
