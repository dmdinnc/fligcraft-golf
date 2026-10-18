package com.ziggleflig.golf.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonObject;
import com.ziggleflig.golf.GolfMod;
import com.ziggleflig.golf.block.entity.SculptedGroundBlockEntity;
import com.ziggleflig.golf.terrain.SculptedSurface;
import com.ziggleflig.golf.terrain.SculptedSurface.Corner;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.BlockModelRotation;
import net.minecraft.client.resources.model.Material;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ModelState;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.geometry.IGeometryBakingContext;
import net.neoforged.neoforge.client.model.geometry.IGeometryLoader;
import net.neoforged.neoforge.client.model.geometry.IUnbakedGeometry;
import net.neoforged.neoforge.client.model.pipeline.QuadBakingVertexConsumer;
import net.neoforged.neoforge.client.model.IQuadTransformer;

/** Static chunk geometry, rebuilt only after editing; never a per-frame terrain renderer. */
public class SculptedGroundGeometry implements IUnbakedGeometry<SculptedGroundGeometry> {
    private final int cell;
    public SculptedGroundGeometry() { this(0); }
    public SculptedGroundGeometry(int cell) { this.cell = cell; }
    public static class Loader implements IGeometryLoader<SculptedGroundGeometry> {
        @Override public SculptedGroundGeometry read(JsonObject json, JsonDeserializationContext context) {
            return new SculptedGroundGeometry(json.has("cap") && json.get("cap").getAsBoolean() ? 1 : 0);
        }
    }

    @Override public BakedModel bake(IGeometryBakingContext context, ModelBaker baker, Function<Material, TextureAtlasSprite> sprites,
            ModelState state, ItemOverrides overrides) {
        List<BakedQuad> pole = new ArrayList<>();
        if (cell == 0) {
            BakedModel flag = baker.bake(ResourceLocation.fromNamespaceAndPath(GolfMod.MODID, "block/golf_flag"), BlockModelRotation.X0_Y0, sprites);
            if (flag != null) {
                pole.addAll(flag.getQuads(null, null, RandomSource.create(0)));
                for (Direction side : Direction.values()) { pole.addAll(flag.getQuads(null, side, RandomSource.create(0))); }
                // Replace the original flat cup with a disk that follows both terrain triangles.
                pole.removeIf(quad -> {
                    for (int i = 0; i < 4; i++) {
                        if (Float.intBitsToFloat(quad.getVertices()[i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1]) > 0.02f) { return false; }
                    }
                    return true;
                });
            }
        }
        return new Model(sprites.apply(context.getMaterial("surface")), context.getTransforms(), overrides, cell,
            List.copyOf(pole), cell == 0 ? sprites.apply(context.getMaterial("hole")) : null);
    }

    public static class Model implements BakedModel {
        private final TextureAtlasSprite sprite;
        private final ItemTransforms transforms;
        private final ItemOverrides overrides;
        private final int cell;
        private final List<BakedQuad> pole;
        private final TextureAtlasSprite hole;
        private final LoadingCache<SculptedSurface, List<BakedQuad>> meshes = CacheBuilder.newBuilder().maximumSize(256)
            .build(new CacheLoader<>() {
                @Override public List<BakedQuad> load(SculptedSurface surface) { return mesh(surface); }
            });
        private final LoadingCache<SculptedSurface, List<BakedQuad>> flags = CacheBuilder.newBuilder().maximumSize(256)
            .build(new CacheLoader<>() {
                @Override public List<BakedQuad> load(SculptedSurface surface) { return flagMesh(surface); }
            });

        public Model(TextureAtlasSprite sprite, ItemTransforms transforms, ItemOverrides overrides) {
            this(sprite, transforms, overrides, 0);
        }
        public Model(TextureAtlasSprite sprite, ItemTransforms transforms, ItemOverrides overrides, int cell) {
            this(sprite, transforms, overrides, cell, List.of(), sprite);
        }
        public Model(TextureAtlasSprite sprite, ItemTransforms transforms, ItemOverrides overrides, int cell, List<BakedQuad> pole, TextureAtlasSprite hole) {
            this.sprite = sprite; this.transforms = transforms; this.overrides = overrides; this.cell = cell;
            this.pole = pole; this.hole = hole;
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            return getQuads(state, side, random, ModelData.EMPTY, null);
        }
        @Override public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random, ModelData data, RenderType renderType) {
            if (side != null) { return List.of(); }
            SculptedSurface surface = data.get(SculptedGroundBlockEntity.SURFACE);
            surface = surface == null ? SculptedSurface.FULL : surface;
            return meshes.getUnchecked(surface);
        }

        /** The flag renderer lights these cached quads at their actual positions above the terrain. */
        public List<BakedQuad> flagQuads(SculptedSurface surface) { return cell == 0 ? flags.getUnchecked(surface) : List.of(); }

        private List<BakedQuad> flagMesh(SculptedSurface surface) {
            List<BakedQuad> quads = new ArrayList<>();
            double height = surface.heightAt(0.5, 0.5);
            for (BakedQuad quad : pole) {
                int[] vertices = quad.getVertices().clone();
                for (int i = 0; i < 4; i++) {
                    int y = i * IQuadTransformer.STRIDE + IQuadTransformer.POSITION + 1;
                    vertices[y] = Float.floatToRawIntBits((float) (Float.intBitsToFloat(vertices[y]) + height));
                }
                quads.add(new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade(), quad.hasAmbientOcclusion()));
            }
            Vec3 center = new Vec3(0.5, height + 0.002, 0.5);
            for (int i = 0; i < 16; i++) {
                double a = i * Math.PI / 8, b = (i + 1) * Math.PI / 8;
                Vec3 first = cupEdge(surface, a), second = cupEdge(surface, b);
                Vec3 normal = surface.normalAt((center.x + first.x + second.x) / 3, (center.z + first.z + second.z) / 3);
                quads.add(quad(hole, Direction.UP, normal, true, center, second, first, first));
            }
            return List.copyOf(quads);
        }
        private static Vec3 cupEdge(SculptedSurface surface, double angle) {
            double x = 0.5 + Math.cos(angle) * 2.5 / 16, z = 0.5 + Math.sin(angle) * 2.5 / 16;
            return new Vec3(x, surface.heightAt(x, z) + 0.002, z);
        }

        private Vec3 top(SculptedSurface surface, Corner corner) {
            return new Vec3(corner.x, surface.height(corner) / 16.0, corner.z);
        }
        private List<BakedQuad> mesh(SculptedSurface surface) {
            List<BakedQuad> quads = new ArrayList<>();
            Vec3 nw = top(surface, Corner.NORTH_WEST), ne = top(surface, Corner.NORTH_EAST);
            Vec3 se = top(surface, Corner.SOUTH_EAST), sw = top(surface, Corner.SOUTH_WEST);
            polygon(quads, Direction.UP, surface.normalAt(0.25, 0.75), true, List.of(nw, sw, se));
            polygon(quads, Direction.UP, surface.normalAt(0.75, 0.25), true, List.of(nw, se, ne));
            if (!surface.isEmpty()) {
                side(quads, Direction.NORTH, nw, ne);
                side(quads, Direction.EAST, ne, se);
                side(quads, Direction.SOUTH, se, sw);
                side(quads, Direction.WEST, sw, nw);
                if (cell == 0) {
                    quads.add(quad(Direction.DOWN, new Vec3(0, -1, 0), true,
                        new Vec3(0, 0, 0), new Vec3(1, 0, 0), new Vec3(1, 0, 1), new Vec3(0, 0, 1)));
                }
            }
            return List.copyOf(quads);
        }
        private void side(List<BakedQuad> quads, Direction direction, Vec3 a, Vec3 b) {
            if (a.y <= 1.0 / 1024 && b.y <= 1.0 / 1024) { return; }
            polygon(quads, direction, Vec3.atLowerCornerOf(direction.getNormal()), false,
                List.of(new Vec3(a.x, 0, a.z), a, b, new Vec3(b.x, 0, b.z)));
        }
        private void polygon(List<BakedQuad> quads, Direction direction, Vec3 normal, boolean horizontal, List<Vec3> vertices) {
            // Only a ground-level flat face needs a tiny offset to avoid fighting the block underneath.
            if (direction == Direction.UP && vertices.stream().allMatch(vertex -> vertex.y == 0)) {
                vertices = vertices.stream().map(vertex -> vertex.add(0, 1.0 / 1024, 0)).toList();
            }
            if (cell == 1 && vertices.stream().noneMatch(vertex -> vertex.y > 1.0000001)) { return; }
            List<Vec3> clipped = clip(clip(vertices, cell, true), cell + 1, false);
            if (clipped.size() < 3) { return; }
            clipped = clipped.stream().map(vertex -> vertex.add(0, -cell, 0)).toList();
            if (clipped.size() == 4) {
                quads.add(quad(direction, normal, horizontal, clipped.toArray(Vec3[]::new)));
            } else {
                for (int i = 1; i < clipped.size() - 1; i++) {
                    quads.add(quad(direction, normal, horizontal, clipped.getFirst(), clipped.get(i), clipped.get(i + 1), clipped.get(i + 1)));
                }
            }
        }
        private static List<Vec3> clip(List<Vec3> polygon, double plane, boolean above) {
            List<Vec3> output = new ArrayList<>();
            for (int i = 0; i < polygon.size(); i++) {
                Vec3 a = polygon.get(i), b = polygon.get((i + 1) % polygon.size());
                boolean insideA = above ? a.y >= plane : a.y <= plane;
                boolean insideB = above ? b.y >= plane : b.y <= plane;
                if (insideA) { output.add(a); }
                if (insideA != insideB) { output.add(a.lerp(b, (plane - a.y) / (b.y - a.y))); }
            }
            List<Vec3> distinct = new ArrayList<>();
            for (Vec3 point : output) {
                if (distinct.isEmpty() || distinct.getLast().distanceToSqr(point) > 1.0e-16) { distinct.add(point); }
            }
            if (distinct.size() > 1 && distinct.getFirst().distanceToSqr(distinct.getLast()) <= 1.0e-16) { distinct.removeLast(); }
            return distinct;
        }
        private BakedQuad quad(Direction direction, Vec3 normal, boolean horizontal, Vec3... vertices) {
            return quad(sprite, direction, normal, horizontal, vertices);
        }
        private BakedQuad quad(TextureAtlasSprite texture, Direction direction, Vec3 normal, boolean horizontal, Vec3... vertices) {
            QuadBakingVertexConsumer builder = new QuadBakingVertexConsumer();
            builder.setSprite(texture); builder.setDirection(direction); builder.setShade(true);
            builder.setHasAmbientOcclusion(false);
            for (int i = 0; i < vertices.length; i++) {
                Vec3 vertex = vertices[i];
                float u = horizontal ? (float) vertex.x : direction.getAxis() == Direction.Axis.X ? (float) vertex.z : (float) vertex.x;
                float v = horizontal ? (float) vertex.z : (float) (1 - vertex.y);
                builder.addVertex((float) vertex.x, (float) vertex.y, (float) vertex.z).setColor(255, 255, 255, 255)
                    .setUv(texture.getU(u), texture.getV(v)).setUv2(0, 0).setNormal((float) normal.x, (float) normal.y, (float) normal.z);
            }
            return builder.bakeQuad();
        }
        @Override public boolean useAmbientOcclusion() { return false; }
        @Override public boolean isGui3d() { return true; }
        @Override public boolean usesBlockLight() { return true; }
        @Override public boolean isCustomRenderer() { return false; }
        @Override public TextureAtlasSprite getParticleIcon() { return sprite; }
        @Override public ItemTransforms getTransforms() { return transforms; }
        @Override public ItemOverrides getOverrides() { return overrides; }
    }
}
