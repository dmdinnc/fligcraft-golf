package com.ziggleflig.golf;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import com.ziggleflig.golf.entity.GolfBallEntity;
import com.ziggleflig.golf.item.GolfClubItem;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GolfMod.MODID)
@PrefixGameTestTemplate(false)
public class ClubCarryCalibrationTests {
    private static final double METERS_PER_YARD = 0.9144;
    private static final int RANGE_LENGTH = 1024;
    private static final int MAX_SHOT_TICKS = 1600;
    private static final Set<String> CLUBS = Set.of("driver", "hybrid", "iron", "pitching_wedge", "sand_wedge");
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    public record Target(String club, double targetYards, double powerMultiplier) {
        double meters() { return targetYards * METERS_PER_YARD; }
    }
    public record Profile(double toleranceMeters, List<Target> clubs) { }
    public record Shot(double carryMeters, double totalMeters, double flightTicks, double apexMeters, double launchSpeed) { }
    public record ClubResult(String club, double targetYards, double targetMeters, double trialMultiplier,
            Shot fairway, Shot tee, double suggestedMultiplier, Shot suggestedFairway, Shot suggestedTee) { }
    public record Report(String generatedAt, String conditions, double toleranceMeters, List<ClubResult> clubs) { }

    @GameTest(template = "empty", batch = "clubCarry", skyAccess = true)
    public static void carryStopsAtImpactEvenWhenTheSameTickContinuesIntoABounce(GameTestHelper helper) {
        for (int x = 1; x <= 7; x++) for (int z = 2; z <= 4; z++) {
            helper.setBlock(new BlockPos(x, 1, z), GolfMod.FAIRWAY_BLOCK.get());
        }
        var ball = new BenchmarkBall(helper.getLevel(), helper.absolutePos(new BlockPos(2, 1, 3)).getY() + 1);
        ball.setPos(helper.absoluteVec(new Vec3(2.5, 3, 3.5)));
        ball.setDeltaMovement(new Vec3(1.5, -2, 0));
        ball.beginMeasurement();
        ball.tick();
        double carry = ball.carry;
        helper.assertTrue(carry > 0 && ball.getDeltaMovement().y > 0
                && horizontalDistance(ball.position(), ball.start) > carry + 0.2,
            "Carry must stop at the impact substep, before the rest of the bounce tick moves the ball forward");
        for (int tick = 0; tick < 5; tick++) { ball.tick(); }
        helper.assertTrue(ball.carry == carry, "Subsequent bouncing/rolling must never increase recorded carry");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "clubCarry", skyAccess = true)
    public static void benchmarkDetectsACeilingClippingTheInitialFlight(GameTestHelper helper) {
        helper.setBlock(new BlockPos(3, 1, 3), GolfMod.FAIRWAY_BLOCK.get());
        helper.setBlock(new BlockPos(3, 3, 3), Blocks.STONE);
        var ball = new BenchmarkBall(helper.getLevel(), helper.absolutePos(new BlockPos(3, 1, 3)).getY() + 1);
        ball.setPos(helper.absoluteVec(new Vec3(3.5, 2.00001, 3.5)));
        ball.setDeltaMovement(new Vec3(0, 2, 0));
        ball.beginMeasurement();
        ball.tick();
        helper.assertTrue(ball.hitCeiling, "An overhead obstacle must invalidate the calibration instead of silently shortening its shots");
        helper.succeed();
    }

    @GameTest(template = "club_range", batch = "clubCarry", skyAccess = true, timeoutTicks = 200)
    public static void measureAndCalibrateClubCarry(GameTestHelper helper) throws IOException {
        Path reports = Path.of(System.getProperty("fligcraft_golf.carryReportDir", "reports/club-carry"));
        Files.createDirectories(reports);
        int originalCycle = GolfModConfig.COMMON.accuracyMeterCycleMs.get();
        boolean passed = false;
        try {
            Profile profile;
            String profileFile = System.getProperty("fligcraft_golf.carryProfile");
            try (Reader reader = profileFile == null
                    ? new java.io.InputStreamReader(ClubCarryCalibrationTests.class.getResourceAsStream("/club-carry-profile.json"), java.nio.charset.StandardCharsets.UTF_8)
                    : Files.newBufferedReader(Path.of(profileFile))) {
                profile = JSON.fromJson(reader, Profile.class);
            }
            validate(helper, profile);
            // Exact center timing requires a cycle divisible by four. Restore the test server's setting afterward.
            GolfModConfig.COMMON.accuracyMeterCycleMs.set(1800);
            for (int x = 1; x < RANGE_LENGTH - 1; x++) for (int z = 2; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, 1, z), GolfMod.FAIRWAY_BLOCK.get());
            }
            List<ClubResult> results = new ArrayList<>();
            for (Target target : profile.clubs()) {
                Shot fairway = shoot(helper, target.club(), target.powerMultiplier(), false, true);
                Shot tee = shoot(helper, target.club(), target.powerMultiplier(), true, true);
                helper.assertTrue(Math.abs(tee.launchSpeed() / fairway.launchSpeed() - 1.1) < 0.000001,
                    "Tee shots must use the real 10% launch-power bonus for " + target.club());
                double suggested = calibrate(helper, target, profile.toleranceMeters());
                // Validate the rounded, exportable setting with fresh complete shots, not the solver's cached sample.
                Shot tunedFairway = shoot(helper, target.club(), suggested, false, true);
                Shot tunedTee = shoot(helper, target.club(), suggested, true, true);
                helper.assertTrue(Math.abs(tunedFairway.carryMeters() - target.meters()) <= profile.toleranceMeters(),
                    "Calibrated carry missed the target for " + target.club());
                results.add(new ClubResult(target.club(), target.targetYards(), target.meters(), target.powerMultiplier(),
                    fairway, tee, suggested, tunedFairway, tunedTee));
                GolfMod.LOGGER.info("CLUB_CARRY {} target={}m fairway={}m tee={}m suggested={} verified={}m tee={}m",
                    target.club(), target.meters(), fairway.carryMeters(), tee.carryMeters(), suggested,
                    tunedFairway.carryMeters(), tunedTee.carryMeters());
            }
            Report report = new Report(Instant.now().toString(),
                "Open sky; full charge; perfect accuracy; zero wind and spin; flat fairway landing surface; carry at first contact; global multiplier 1.",
                profile.toleranceMeters(), List.copyOf(results));
            writeReports(reports, report);
            if (Boolean.getBoolean("fligcraft_golf.carryVerifyTargets")) {
                helper.assertTrue(results.stream().allMatch(result -> Math.abs(result.fairway().carryMeters() - result.targetMeters()) <= profile.toleranceMeters()),
                    "Trial power multipliers miss carry targets; see build/reports/club-carry/carry.md and suggested settings");
            }
            Files.writeString(reports.resolve("status.txt"), "PASS\n");
            passed = true;
            helper.succeed();
        } finally {
            GolfModConfig.COMMON.accuracyMeterCycleMs.set(originalCycle);
            if (!passed) { Files.writeString(reports.resolve("status.txt"), "FAIL\n"); }
        }
    }

    private static void validate(GameTestHelper helper, Profile profile) {
        helper.assertTrue(profile != null && profile.clubs() != null && profile.clubs().size() == CLUBS.size(),
            "The carry profile must define all five non-putter clubs");
        helper.assertTrue(Double.isFinite(profile.toleranceMeters()) && profile.toleranceMeters() > 0,
            "Carry tolerance must be a finite positive number of meters");
        Set<String> found = new HashSet<>();
        for (Target target : profile.clubs()) {
            helper.assertTrue(target != null && CLUBS.contains(target.club()) && found.add(target.club()), "Unknown or duplicate carry-profile club");
            helper.assertTrue(Double.isFinite(target.targetYards()) && target.targetYards() > 0, "Target yardage must be finite and positive");
            helper.assertTrue(Double.isFinite(target.powerMultiplier()) && target.powerMultiplier() >= GolfClubConfig.MIN_MULTIPLIER
                && target.powerMultiplier() <= GolfClubConfig.MAX_MULTIPLIER, "Trial power multiplier must be within 0.1–5.0");
        }
    }

    private static double calibrate(GameTestHelper helper, Target target, double tolerance) {
        double low = GolfClubConfig.MIN_MULTIPLIER, high = GolfClubConfig.MAX_MULTIPLIER;
        double lowerCarry = shoot(helper, target.club(), low, false, false).carryMeters();
        double upperCarry = shoot(helper, target.club(), high, false, false).carryMeters();
        helper.assertTrue(target.meters() >= lowerCarry && target.meters() <= upperCarry,
            target.club() + " target is outside the supported power range: " + lowerCarry + "–" + upperCarry + " meters");
        double best = low, error = Math.abs(lowerCarry - target.meters());
        for (int iteration = 0; iteration < 20; iteration++) {
            double middle = (low + high) / 2;
            double carry = shoot(helper, target.club(), middle, false, false).carryMeters();
            if (Math.abs(carry - target.meters()) < error) { best = middle; error = Math.abs(carry - target.meters()); }
            if (error <= tolerance / 4) { break; }
            if (carry < target.meters()) { low = middle; } else { high = middle; }
        }
        return Math.round(best * 1_000_000) / 1_000_000.0;
    }

    private static Shot shoot(GameTestHelper helper, String clubName, double multiplier, boolean tee, boolean trackRunout) {
        BlockPos ground = helper.absolutePos(new BlockPos(4, 1, 3)), teePos = ground.above();
        helper.getLevel().setBlockAndUpdate(teePos, tee ? GolfMod.GOLF_TEE_BLOCK.get().defaultBlockState() : Blocks.AIR.defaultBlockState());
        BlockPos support = tee ? teePos : ground;
        double floorY = ground.getY() + 1;
        double startY = support.getY() + helper.getLevel().getBlockState(support).getCollisionShape(helper.getLevel(), support).max(Direction.Axis.Y);
        var ball = new BenchmarkBall(helper.getLevel(), floorY);
        ball.setPos(ground.getX() + 0.5, startY + 0.00001, ground.getZ() + 0.5);
        ball.setOnTee(tee);
        // Match a ball at rest after placement/charging, including the first tick's grounded state.
        for (int tick = 0; tick < 5; tick++) { ball.tick(); }
        helper.assertTrue(ball.onGround(), "Benchmark ball must rest on its actual starting surface");
        helper.getLevel().addFreshEntity(ball);
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "carry-test"));
        player.setPos(ball.getX() - 0.75, ball.getY() - player.getEyeHeight() + 0.125, ball.getZ());
        player.setYRot(-90);
        player.setXRot(0);
        var stack = new ItemStack(club(clubName));
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        try {
            var club = (GolfClubItem) stack.getItem();
            club.releaseUsing(stack, helper.getLevel(), player, club.getUseDuration(stack, player) - 60);
            helper.assertTrue(GolfClubItem.getPendingShot(player) != null, "Club must acquire the benchmark ball");
            double worldMultiplier = GolfClubConfig.forServer(helper.getLevel().getServer()).effectiveMultiplier(clubName);
            GolfClubItem.executePendingShot(player, 450);
            helper.assertTrue(ball.getStrokes() == 1 && ball.getSpin() == 0 && ball.getLastShotErrorPercent() == 0,
                "Benchmark must execute a real full-charge, perfect-accuracy strike");
            // Power factors scale the entire launch vector linearly. Normalize the actual strike to the trial
            // factor without writing shared server settings or altering any flight/contact calculation.
            ball.setDeltaMovement(ball.getDeltaMovement().scale(multiplier / worldMultiplier));
            ball.beginMeasurement();
            double launchSpeed = ball.getDeltaMovement().length();
            int stillTicks = 0;
            for (int tick = 0; tick < MAX_SHOT_TICKS; tick++) {
                ball.tick();
                helper.assertTrue(!ball.hitCeiling, "The carry benchmark requires an open-sky range without ceiling contacts: "
                    + clubName + ", factor=" + multiplier + ", tee=" + tee + ", " + ball.ceilingInfo);
                helper.assertTrue(ball.isAlive() && ball.getX() < ground.getX() + RANGE_LENGTH - 8 && Math.abs(ball.getZ() - ground.getZ() - 0.5) < 0.01,
                    "A benchmark shot left the loaded, wind-free fairway range");
                if (Double.isNaN(ball.carry)) { continue; }
                if (!trackRunout) { break; }
                stillTicks = ball.onGround() && ball.getDeltaMovement().lengthSqr() < 0.00000001 ? stillTicks + 1 : 0;
                if (stillTicks >= 3) { break; }
            }
            helper.assertTrue(Double.isFinite(ball.carry) && (!trackRunout || stillTicks >= 3),
                clubName + " must land on the fairway and finish within the benchmark tick budget: factor=" + multiplier
                    + ", tee=" + tee + ", carry=" + ball.carry + ", floor=" + floorY + ", position=" + ball.position()
                    + ", motion=" + ball.getDeltaMovement() + ", grounded=" + ball.onGround() + ", stillTicks=" + stillTicks);
            double total = horizontalDistance(ball.position(), ball.start);
            helper.assertTrue(total >= ball.carry - 0.0001, "Carry must not include later bounce/roll distance");
            return new Shot(ball.carry, total, ball.flightTicks, ball.apex - ball.start.y, launchSpeed);
        } finally {
            GolfClubItem.clearPendingShot(player.getUUID());
            ball.discard();
            helper.getLevel().setBlockAndUpdate(teePos, Blocks.AIR.defaultBlockState());
        }
    }

    private static Item club(String name) {
        return switch (name) {
            case "driver" -> GolfMod.DRIVER.get();
            case "hybrid" -> GolfMod.HYBRID.get();
            case "iron" -> GolfMod.IRON.get();
            case "pitching_wedge" -> GolfMod.PITCHING_WEDGE.get();
            case "sand_wedge" -> GolfMod.SAND_WEDGE.get();
            default -> throw new IllegalArgumentException("Unknown carry-profile club: " + name);
        };
    }

    private static void writeReports(Path directory, Report report) throws IOException {
        Files.writeString(directory.resolve("carry.json"), JSON.toJson(report) + "\n");
        var csv = new StringBuilder("club,target_yards,target_meters,trial_multiplier,fairway_carry_m,tee_carry_m,fairway_total_m,tee_total_m,suggested_multiplier,verified_fairway_carry_m,verified_tee_carry_m\n");
        var markdown = new StringBuilder("# Club carry calibration\n\n" + report.conditions() + "\n\n")
            .append("| Club | Target yd | Target m | Trial factor | Fairway carry m | Tee carry m | Suggested factor | Verified fairway m | Verified tee m |\n")
            .append("| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |\n");
        var toml = new StringBuilder("# Suggested settings, validated against fairway CARRY (before bounce/roll).\n[clubs]\nglobalPowerMultiplier = 1.0\n");
        for (ClubResult result : report.clubs()) {
            csv.append(String.format(Locale.ROOT, "%s,%.3f,%.3f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f,%.6f%n",
                result.club(), result.targetYards(), result.targetMeters(), result.trialMultiplier(), result.fairway().carryMeters(), result.tee().carryMeters(),
                result.fairway().totalMeters(), result.tee().totalMeters(), result.suggestedMultiplier(), result.suggestedFairway().carryMeters(), result.suggestedTee().carryMeters()));
            markdown.append(String.format(Locale.ROOT, "| %s | %.0f | %.3f | %.6f | %.3f | %.3f | %.6f | %.3f | %.3f |%n",
                result.club(), result.targetYards(), result.targetMeters(), result.trialMultiplier(), result.fairway().carryMeters(), result.tee().carryMeters(),
                result.suggestedMultiplier(), result.suggestedFairway().carryMeters(), result.suggestedTee().carryMeters()));
            String configKey = switch (result.club()) {
                case "pitching_wedge" -> "pitchingWedge";
                case "sand_wedge" -> "sandWedge";
                default -> result.club();
            };
            toml.append(String.format(Locale.ROOT, "%sPowerMultiplier = %.6f%n", configKey, result.suggestedMultiplier()));
        }
        markdown.append("\nCarry is captured at the first contact substep. Total distance is reported separately in CSV/JSON; flight time and apex are in JSON. None of those additional measurements are tuning targets.\n");
        Files.writeString(directory.resolve("carry.csv"), csv);
        Files.writeString(directory.resolve("carry.md"), markdown);
        Files.writeString(directory.resolve("suggested-clubs.toml"), toml.append("# Putter is not calibrated; choose its setting separately.\n"));
        Profile suggestedProfile = new Profile(report.toleranceMeters(), report.clubs().stream()
            .map(result -> new Target(result.club(), result.targetYards(), result.suggestedMultiplier())).toList());
        Files.writeString(directory.resolve("suggested-profile.json"), JSON.toJson(suggestedProfile) + "\n");
    }

    private static double horizontalDistance(Vec3 a, Vec3 b) { return Math.hypot(a.x - b.x, a.z - b.z); }

    private static final class BenchmarkBall extends GolfBallEntity {
        private final double floorY;
        private Vec3 start;
        private double carry = Double.NaN, elapsed, flightTicks, apex;
        private boolean hitCeiling;
        private String ceilingInfo;

        private BenchmarkBall(Level level, double floorY) { super(GolfMod.GOLF_BALL_ENTITY.get(), level); this.floorY = floorY; }
        private void beginMeasurement() { start = position(); apex = getY(); }
        @Override protected Vec3 applyWind(Vec3 motion) { return motion; }
        @Override public void setOnGround(boolean grounded) {
            super.setOnGround(grounded);
            // The support cast may settle a near-contact ball between move callbacks, without a rebound.
            if (grounded) { recordContact(); }
        }
        private void recordContact() {
            if (start != null && Double.isNaN(carry) && getY() <= floorY + 0.00002) {
                carry = horizontalDistance(position(), start);
                flightTicks = elapsed;
            }
        }
        @Override protected void onPhysicsMove(Vec3 previousPosition, Vec3 approachVelocity, double elapsedTicks) {
            if (start != null) {
                elapsed += elapsedTicks;
                apex = Math.max(apex, getY());
                if (Double.isNaN(carry) && getY() > floorY + 0.01 && approachVelocity.y > 0 && elapsedTicks > 0.000001
                        && getY() - previousPosition.y < approachVelocity.y * elapsedTicks - 0.0001) {
                    hitCeiling = true;
                    ceilingInfo = "previous=" + previousPosition + ", position=" + position() + ", approach=" + approachVelocity + ", time=" + elapsedTicks;
                }
                if (approachVelocity.y < 0 && previousPosition.y >= floorY - 0.00002) { recordContact(); }
            }
            super.onPhysicsMove(previousPosition, approachVelocity, elapsedTicks);
        }
    }
}
