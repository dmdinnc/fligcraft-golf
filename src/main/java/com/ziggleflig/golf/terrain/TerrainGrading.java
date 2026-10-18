package com.ziggleflig.golf.terrain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import com.ziggleflig.golf.block.SculptedGroundBlock;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.terrain.SculptedSurface.Corner;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;

/** A bounded, read-only plan followed by a server-side preflight and a single batch edit. */
public final class TerrainGrading {
    public static final int MAX_SIDE = 64;
    public enum Mode { PLANE, BLEND }
    public record Anchor(BlockPos source, Corner corner, int height) {
        public int x() { return source.getX() + corner.x; }
        public int z() { return source.getZ() + corner.z; }
    }
    public record Problem(String key, BlockPos pos) {
        public Component message() {
            return Component.translatable("message.fligcraft_golf.grader." + key,
                pos == null ? "" : pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
        }
    }
    public record Edit(BlockPos pos, BlockState state, SculptedSurface before, SculptedSurface after) {
        public Edit reversed() { return new Edit(pos, SculptedGroundBlock.stateForSurface(state, after), after, before); }
    }
    public record Fill(BlockPos pos, BlockState before, BlockState state, SculptedSurface surface) {
        public Fill { state = SculptedGroundBlock.stateForSurface(state, surface); }
    }
    public record Plan(GradingArea area, List<Edit> edits, List<Problem> problems, List<Fill> fills) {
        public Plan(GradingArea area, List<Edit> edits, List<Problem> problems) { this(area, edits, problems, List.of()); }
        public boolean valid() { return problems.isEmpty(); }
        public boolean localProblemsOnly() {
            return problems.stream().allMatch(problem -> problem.key().equals("range") || problem.key().equals("blocked") || problem.key().equals("missing"));
        }
        /** Local geometry problems can be skipped; incomplete or stale selections cannot. */
        public boolean canApplyPartial() {
            return !valid() && (!edits.isEmpty() || !fills.isEmpty()) && localProblemsOnly();
        }
        public Problem problem() { return valid() ? null : problems.getFirst(); }
        public Component message() {
            return problems.size() == 1 ? problem().message()
                : Component.translatable("message.fligcraft_golf.grader.problems", problems.size(), problem().message());
        }
    }

    private record Tile(BlockPos getBlockPos, BlockState getBlockState, SculptedSurface surface, boolean hasFlag) { }
    private static Tile tile(Level level, BlockPos pos) {
        var ground = SculptedGroundBlock.source(level, pos);
        return ground == null ? null : new Tile(ground.getBlockPos(), ground.getBlockState(), ground.surface(), ground.hasFlag());
    }

    public static Plan plan(Level level, List<Anchor> anchors, Mode mode) {
        return plan(level, anchors, mode, false);
    }
    /** Simulate additional terrain without placing anything until the player confirms. */
    public static Plan planWithFills(Level level, List<Anchor> anchors, Mode mode) {
        return plan(level, anchors, mode, true);
    }
    private static Plan plan(Level level, List<Anchor> anchors, Mode mode, boolean fillGaps) {
        GradingArea area = GradingArea.create(anchors);
        if (area == null) { return fail(null, "area", null); }
        if (area.width() > MAX_SIDE || area.depth() > MAX_SIDE) { return fail(null, "size", null); }
        List<Vertex> vertices = new ArrayList<>();
        for (int vx = area.minX(); vx <= area.maxX(); vx++) {
            for (int vz = area.minZ(); vz <= area.maxZ(); vz++) {
                if (!area.contains(vx, vz)) { continue; }
                vertices.add(new Vertex(vx, vz, area.height(mode, vx, vz)));
                // Every touching tile must be loaded, including those outside the selected polygon.
                for (int x = vx - 1; x <= vx; x++) {
                    for (int z = vz - 1; z <= vz; z++) {
                        if (!level.hasChunk(x >> 4, z >> 4)) { return fail(area, "unloaded", new BlockPos(x, 0, z)); }
                    }
                }
            }
        }
        Map<Long, Tile> anchorSources = new HashMap<>();
        for (Anchor anchor : anchors) {
            var ground = tile(level, anchor.source());
            if (ground == null || !ground.getBlockPos().equals(anchor.source())
                    || ground.getBlockPos().getY() * 16 + ground.surface().height(anchor.corner()) != anchor.height()) {
                return fail(area, "changed", anchor.source());
            }
            var previous = anchorSources.put(column(anchor.source().getX(), anchor.source().getZ()), ground);
            if (previous != null && !previous.getBlockPos().equals(ground.getBlockPos())) { return fail(area, "layers", anchor.source()); }
        }
        Map<Long, Tile> inside = new LinkedHashMap<>();
        Map<BlockPos, Fill> fills = new LinkedHashMap<>();
        Map<BlockPos, Edit> foundations = new LinkedHashMap<>();
        Map<BlockPos, SculptedSurface> surfaces = new LinkedHashMap<>();
        var problems = new LinkedHashSet<Problem>();
        var outsideRange = new LinkedHashSet<BlockPos>();
        for (int x = area.minX(); x < area.maxX(); x++) {
            for (int z = area.minZ(); z < area.maxZ(); z++) {
                if (!area.contains(x + 0.5, z + 0.5)) { continue; }
                int average = area.height(mode, x + 0.5, z + 0.5);
                if (!level.hasChunk(x >> 4, z >> 4)) { return fail(area, "unloaded", new BlockPos(x, 0, z)); }
                var ground = anchorSources.get(column(x, z));
                if (ground == null) { ground = columnGround(level, x, z, average); }
                if (fillGaps) {
                    Tile repaired = fillTile(level, area, mode, x, z, ground, fills, foundations);
                    if (repaired != null) { ground = repaired; }
                }
                if (ground == null) { problems.add(new Problem("missing", new BlockPos(x, Math.floorDiv(average, 16), z))); continue; }
                inside.put(column(x, z), ground);
            }
        }
        // Edit selected grid vertices, including their boundary, in all tiles sharing the vertex.
        // Unselected vertices on partially covered tiles remain intact; no bounding-box spillover.
        for (Vertex vertex : vertices) {
            Tile reference = null;
            for (int x = vertex.x - 1; x <= vertex.x && reference == null; x++) {
                for (int z = vertex.z - 1; z <= vertex.z && reference == null; z++) { reference = inside.get(column(x, z)); }
            }
            if (reference == null) {
                for (Anchor anchor : anchors) { if (anchor.x() == vertex.x && anchor.z() == vertex.z) { reference = tile(level, anchor.source()); break; } }
            }
            if (reference == null) {
                for (int x = vertex.x - 1; x <= vertex.x && reference == null; x++) {
                    for (int z = vertex.z - 1; z <= vertex.z && reference == null; z++) { reference = columnGround(level, x, z, vertex.height); }
                }
            }
            if (reference == null) { problems.add(new Problem("missing", new BlockPos(vertex.x, Math.floorDiv(vertex.height, 16), vertex.z))); continue; }
            Corner referenceCorner = Corner.nearest(vertex.x - reference.getBlockPos().getX(), vertex.z - reference.getBlockPos().getZ());
            int oldHeight = reference.getBlockPos().getY() * 16 + reference.surface().height(referenceCorner);
            for (int x = vertex.x - 1; x <= vertex.x; x++) {
                for (int z = vertex.z - 1; z <= vertex.z; z++) {
                    Corner corner = Corner.nearest(vertex.x - x, vertex.z - z);
                    var ground = inside.get(column(x, z));
                    if (ground == null) { ground = anchorSources.get(column(x, z)); }
                    if (ground == null) { ground = touchingGround(level, x, z, reference.getBlockPos().getY(), corner, oldHeight); }
                    if (ground == null) { continue; }
                    int local = vertex.height - ground.getBlockPos().getY() * 16;
                    if (local < 0 || local > SculptedSurface.MAX_HEIGHT) {
                        problems.add(new Problem("range", ground.getBlockPos()));
                        outsideRange.add(ground.getBlockPos());
                        continue;
                    }
                    SculptedSurface previous = surfaces.getOrDefault(ground.getBlockPos(), ground.surface());
                    surfaces.put(ground.getBlockPos(), previous.withCorner(corner, local));
                }
            }
        }
        List<Edit> edits = new ArrayList<>(foundations.values());
        for (var entry : surfaces.entrySet()) {
            Fill fill = fills.get(entry.getKey());
            var ground = fill == null ? tile(level, entry.getKey()) : new Tile(fill.pos(), fill.state(), fill.surface(), false);
            if (outsideRange.contains(entry.getKey())) { continue; }
            if (!TerrainPlacement.canFit(level, entry.getKey(), entry.getValue())) { problems.add(new Problem("blocked", entry.getKey().above())); continue; }
            if (fill != null) {
                fills.put(fill.pos(), new Fill(fill.pos(), fill.before(), fill.state(), entry.getValue()));
                continue;
            }
            if (!ground.surface().equals(entry.getValue())) {
                edits.add(new Edit(entry.getKey(), ground.getBlockState(), ground.surface(), entry.getValue()));
            }
        }
        return new Plan(area, List.copyOf(edits), List.copyOf(problems), List.copyOf(fills.values()));
    }
    private record Vertex(int x, int z, int height) { }

    private static Tile fillTile(Level level, GradingArea area, Mode mode, int x, int z, Tile existing,
            Map<BlockPos, Fill> fills, Map<BlockPos, Edit> foundations) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (Corner corner : Corner.values()) {
            if (!area.contains(x + corner.x, z + corner.z)) { continue; }
            int height = area.height(mode, x + corner.x, z + corner.z);
            min = Math.min(min, height); max = Math.max(max, height);
        }
        if (min == Integer.MAX_VALUE || existing != null && min >= existing.getBlockPos().getY() * 16
                && max <= existing.getBlockPos().getY() * 16 + SculptedSurface.MAX_HEIGHT) { return null; }
        // A new block can raise a low column, but cannot remove a high block or exceed the corner bounds.
        int lowestBase = -Math.floorDiv(-(max - SculptedSurface.MAX_HEIGHT), 16);
        int highestBase = Math.floorDiv(min, 16);
        if (lowestBase > highestBase) { return null; }
        // Prefer a single upper tile over an unnecessary extra foundation. For a
        // missing column, follow the nearest selected course tile's base level.
        int preferredBase = Math.floorDiv(min - 1, 16);
        if (existing != null) {
            preferredBase = existing.getBlockPos().getY() + 1;
        } else {
            long nearest = Long.MAX_VALUE;
            for (Anchor anchor : area.boundary()) {
                long dx = anchor.x() - x, dz = anchor.z() - z;
                long distance = dx * dx + dz * dz;
                if (distance < nearest) { nearest = distance; preferredBase = anchor.source().getY(); }
            }
        }
        int y = Math.clamp(preferredBase, lowestBase, highestBase);
        if (existing != null && (y <= existing.getBlockPos().getY() || existing.hasFlag())) { return null; }
        BlockPos top = new BlockPos(x, y, z);
        if (level.isOutsideBuildHeight(top) || max > y * 16 + 16 && level.isOutsideBuildHeight(top.above()) || !level.getBlockState(top.above()).isAir()
                && !(level.getBlockState(top.above()).getBlock() instanceof SculptedGroundBlock block && block.isCap())) { return null; }
        int bottomY = existing == null ? y : existing.getBlockPos().getY() + 1;
        if (existing == null) {
            // The preview searches near the target surface. A foundation can be farther below it.
            for (int depth = 1; depth <= 8; depth++) {
                BlockPos support = top.below(depth);
                if (level.isOutsideBuildHeight(support)) { break; }
                if (level.getBlockState(support).isAir()) { continue; }
                Tile owner = tile(level, support);
                if (owner != null && owner.hasFlag()) { return null; }
                bottomY = owner == null ? support.getY() + 1 : owner.getBlockPos().getY() + 1;
                break;
            }
        }
        if (y - bottomY >= 8) { return null; } // Bound automatic foundations instead of filling deep voids.
        Map<BlockPos, Fill> proposed = new LinkedHashMap<>();
        Map<BlockPos, Edit> trims = new LinkedHashMap<>();
        for (int by = bottomY; by <= y; by++) {
            BlockPos pos = new BlockPos(x, by, z);
            BlockState before = level.getBlockState(pos);
            if (level.isOutsideBuildHeight(pos)) { return null; }
            if (!before.isAir()) {
                if (!(before.getBlock() instanceof SculptedGroundBlock block) || !block.isCap()) { return null; }
                Tile owner = tile(level, pos);
                if (owner == null || owner.hasFlag() || !owner.surface().hasOverflow()) { return null; }
                trims.put(owner.getBlockPos(), new Edit(owner.getBlockPos(), owner.getBlockState(), owner.surface(), owner.surface().trimOverflow()));
            }
            Tile below = tile(level, pos.below());
            if (below != null && below.hasFlag()) { return null; }
            BlockState state = fillingMaterial(level, pos, proposed);
            SculptedSurface surface = by == y ? TerrainPlacement.fit(level, pos) : SculptedSurface.FULL;
            proposed.put(pos, new Fill(pos, before, state, surface));
        }
        fills.putAll(proposed); foundations.putAll(trims);
        Fill fill = fills.get(top);
        return new Tile(top, fill.state(), fill.surface(), false);
    }
    /** Below first, then the surrounding majority; green, fairway, rough, sand resolve ties. */
    private static BlockState fillingMaterial(Level level, BlockPos pos, Map<BlockPos, Fill> proposed) {
        Fill belowFill = proposed.get(pos.below());
        if (belowFill != null) { return belowFill.state(); }
        Tile below = tile(level, pos.below());
        if (below != null) { return below.getBlockState(); }
        var choices = new SculptedGroundBlock[] {GolfMod.PUTTING_GREEN_BLOCK.get(), GolfMod.FAIRWAY_BLOCK.get(), GolfMod.ROUGH_BLOCK.get(), GolfMod.BUNKER_SAND_BLOCK.get()};
        int[] counts = new int[choices.length];
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0 || !level.hasChunk((pos.getX() + dx) >> 4, (pos.getZ() + dz) >> 4)) { continue; }
            Tile neighbor = null;
            for (int dy : new int[] {0, -1, 1}) {
                neighbor = tile(level, pos.offset(dx, dy, dz));
                if (neighbor != null) { break; }
            }
            if (neighbor == null) { continue; }
            for (int i = 0; i < choices.length; i++) { if (neighbor.getBlockState().is(choices[i])) { counts[i]++; } }
        }
        int selected = 0;
        for (int i = 1; i < counts.length; i++) { if (counts[i] > counts[selected]) { selected = i; } }
        return choices[selected].defaultBlockState();
    }

    /** Find existing terrain near the proposed surface, without loading chunks or moving blocks. */
    private static Tile columnGround(Level level, int x, int z, int height) {
        int y = Math.floorDiv(height, 16);
        Tile best = null;
        double distance = Double.MAX_VALUE;
        for (int offset = -2; offset <= 1; offset++) {
            BlockPos pos = new BlockPos(x, y + offset, z);
            if (level.isOutsideBuildHeight(pos)) { continue; }
            var ground = tile(level, pos);
            if (ground == null) { continue; }
            if (level.getBlockState(ground.getBlockPos().above()).getBlock() instanceof SculptedGroundBlock above && !above.isCap()) { continue; }
            double top = ground.getBlockPos().getY() * 16 + ground.surface().heightAt(0.5, 0.5) * 16;
            if (Math.abs(top - height) < distance) { best = ground; distance = Math.abs(top - height); }
        }
        return best;
    }

    private static Tile touchingGround(Level level, int x, int z, int y, Corner corner, int oldHeight) {
        Tile best = null;
        int distance = Integer.MAX_VALUE;
        for (int offset : new int[] {0, 1, -1}) {
            var ground = tile(level, new BlockPos(x, y + offset, z));
            if (ground == null) { continue; }
            if (level.getBlockState(ground.getBlockPos().above()).getBlock() instanceof SculptedGroundBlock above && !above.isCap()) { continue; }
            int delta = Math.abs(ground.getBlockPos().getY() * 16 + ground.surface().height(corner) - oldHeight);
            if (delta < distance) { best = ground; distance = delta; }
        }
        return best;
    }

    /** Re-check the complete batch, including caps and protection hooks, before changing any heights. */
    public static Problem apply(ServerLevel level, Player player, ItemStack tool, List<Edit> edits) {
        return apply(level, player, tool, edits, List.of());
    }
    /** Reserve complete columns against the inventory; never leave a half-built foundation. */
    public static Plan availableFills(Player player, Plan plan) {
        if (player.getAbilities().instabuild || plan.fills().isEmpty()) { return plan; }
        var available = new HashMap<net.minecraft.world.item.Item, Integer>();
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            available.merge(stack.getItem(), stack.getCount(), Integer::sum);
        }
        var columns = new LinkedHashMap<Long, List<Fill>>();
        for (Fill fill : plan.fills()) { columns.computeIfAbsent(column(fill.pos().getX(), fill.pos().getZ()), key -> new ArrayList<>()).add(fill); }
        var rejected = new LinkedHashSet<Long>();
        var problems = new ArrayList<>(plan.problems());
        for (var entry : columns.entrySet()) {
            var needed = new HashMap<net.minecraft.world.item.Item, Integer>();
            for (Fill fill : entry.getValue()) { needed.merge(fill.state().getBlock().asItem(), 1, Integer::sum); }
            if (needed.entrySet().stream().anyMatch(cost -> available.getOrDefault(cost.getKey(), 0) < cost.getValue())) {
                rejected.add(entry.getKey()); problems.add(new Problem("materials", entry.getValue().getLast().pos()));
            } else { needed.forEach((item, count) -> available.merge(item, -count, Integer::sum)); }
        }
        return new Plan(plan.area(), plan.edits().stream().filter(edit -> !rejected.contains(column(edit.pos().getX(), edit.pos().getZ()))).toList(),
            List.copyOf(problems), plan.fills().stream().filter(fill -> !rejected.contains(column(fill.pos().getX(), fill.pos().getZ()))).toList());
    }
    public static Problem apply(ServerLevel level, Player player, ItemStack tool, List<Edit> edits, List<Fill> fills) {
        var positions = new LinkedHashSet<BlockPos>();
        for (Edit edit : edits) {
            Problem problem = check(level, edit);
            if (problem != null) { return problem; }
            positions.add(edit.pos());
            if (edit.before().hasOverflow() || edit.after().hasOverflow()) { positions.add(edit.pos().above()); }
        }
        for (Fill fill : fills) {
            Problem problem = checkFill(level, fill, false);
            if (problem != null) { return problem; }
            positions.add(fill.pos());
            if (fill.surface().hasOverflow()) { positions.add(fill.pos().above()); }
        }
        for (BlockPos pos : positions) {
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, tool)
                    || NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos),
                        level.getBlockState(pos.below()), player)).isCanceled()) { return new Problem("protected", pos); }
        }
        // Protection handlers may themselves alter a block: do not apply a now-stale plan.
        for (Edit edit : edits) {
            Problem problem = check(level, edit);
            if (problem != null) { return problem; }
        }
        for (Fill fill : fills) {
            Problem problem = checkFill(level, fill, false);
            if (problem != null) { return problem; }
        }
        if (!player.getAbilities().instabuild) {
            Plan affordable = availableFills(player, new Plan(null, edits, List.of(), fills));
            if (affordable.fills().size() != fills.size()) { return new Problem("materials", fills.getFirst().pos()); }
            for (Fill fill : fills) { consume(player, fill.state().getBlock().asItem()); }
        }
        for (Edit edit : edits) { SculptedGroundBlock.source(level, edit.pos()).setSurface(edit.after()); }
        for (Fill fill : fills) {
            level.setBlock(fill.pos(), fill.state(), 3);
            ((SculptedGroundBlockEntity) level.getBlockEntity(fill.pos())).setSurface(fill.surface());
        }
        return null;
    }
    private static void consume(Player player, net.minecraft.world.item.Item item) {
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            var stack = player.getInventory().getItem(i);
            if (stack.is(item) && !stack.isEmpty()) { stack.shrink(1); return; }
        }
    }
    private static Problem checkFill(ServerLevel level, Fill fill, boolean undo) {
        if (!level.hasChunk(fill.pos().getX() >> 4, fill.pos().getZ() >> 4)) { return new Problem("unloaded", fill.pos()); }
        if (!undo) {
            if (!level.getBlockState(fill.pos()).equals(fill.before())) { return new Problem("changed", fill.pos()); }
            var below = SculptedGroundBlock.source(level, fill.pos().below());
            if (below != null && below.hasFlag()) { return new Problem("changed", below.getBlockPos()); }
            return TerrainPlacement.canFit(level, fill.pos(), fill.surface()) ? null : new Problem("blocked", fill.pos().above());
        }
        var ground = SculptedGroundBlock.source(level, fill.pos());
        if (ground == null || !ground.getBlockPos().equals(fill.pos()) || !ground.getBlockState().equals(fill.state())
                || !ground.surface().equals(fill.surface()) || ground.hasFlag()) { return new Problem("changed", fill.pos()); }
        if (fill.surface().hasOverflow()) {
            var cap = level.getBlockEntity(fill.pos().above());
            if (!(cap instanceof SculptedGroundBlockEntity upper) || !upper.isCap() || !upper.surface().equals(fill.surface())) {
                return new Problem("changed", fill.pos().above());
            }
        }
        return null;
    }
    public static Problem undo(ServerLevel level, Player player, ItemStack tool, List<Edit> edits, List<Fill> fills) {
        if (fills.isEmpty()) { return apply(level, player, tool, edits); }
        var removed = fills.stream().map(Fill::pos).collect(java.util.stream.Collectors.toSet());
        var positions = new LinkedHashSet<BlockPos>();
        for (Fill fill : fills) {
            Problem problem = checkFill(level, fill, true);
            if (problem != null) { return problem; }
            positions.add(fill.pos());
            if (fill.surface().hasOverflow()) { positions.add(fill.pos().above()); }
        }
        for (Edit edit : edits) {
            Problem problem = check(level, edit, removed);
            if (problem != null) { return problem; }
            positions.add(edit.pos());
            if (edit.before().hasOverflow() || edit.after().hasOverflow()) { positions.add(edit.pos().above()); }
        }
        for (BlockPos pos : positions) {
            if (!level.mayInteract(player, pos) || !player.mayUseItemAt(pos, Direction.UP, tool)
                    || NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos), level.getBlockState(pos.below()), player)).isCanceled()
                    || removed.contains(pos) && NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, level.getBlockState(pos), player)).isCanceled()) {
                return new Problem("protected", pos);
            }
        }
        for (Fill fill : fills) { Problem problem = checkFill(level, fill, true); if (problem != null) { return problem; } }
        for (Edit edit : edits) { Problem problem = check(level, edit, removed); if (problem != null) { return problem; } }
        for (Fill fill : fills.reversed()) { level.setBlock(fill.pos(), Blocks.AIR.defaultBlockState(), 3); }
        for (Edit edit : edits) { SculptedGroundBlock.source(level, edit.pos()).setSurface(edit.after()); }
        for (Fill fill : fills) { if (fill.before().isAir()) { level.setBlock(fill.pos(), fill.before(), 3); } }
        return null;
    }
    private static Problem check(ServerLevel level, Edit edit) {
        return check(level, edit, java.util.Set.of());
    }
    private static Problem check(ServerLevel level, Edit edit, java.util.Set<BlockPos> removed) {
        if (!level.hasChunk(edit.pos().getX() >> 4, edit.pos().getZ() >> 4)) { return new Problem("unloaded", edit.pos()); }
        var ground = SculptedGroundBlock.source(level, edit.pos());
        if (ground == null || !ground.getBlockPos().equals(edit.pos()) || !ground.getBlockState().equals(edit.state())
                || !ground.surface().equals(edit.before())) { return new Problem("changed", edit.pos()); }
        return ground.canAccommodate(edit.after()) || removed.contains(edit.pos().above()) ? null : new Problem("blocked", edit.pos().above());
    }
    private static Plan fail(GradingArea area, String key, BlockPos pos) {
        return new Plan(area, List.of(), List.of(new Problem(key, pos)));
    }
    private static long column(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private TerrainGrading() { }
}
