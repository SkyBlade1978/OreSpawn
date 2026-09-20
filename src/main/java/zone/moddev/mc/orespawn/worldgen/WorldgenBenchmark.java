package zone.moddev.mc.orespawn.worldgen;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.mojang.datafixers.util.Pair;
import zone.moddev.mc.orespawn.OreSpawnConfig.GeologyMode;
import zone.moddev.mc.orespawn.worldgen.FormationSettings.Preset;

import net.minecraft.core.Holder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Opt-in integrated benchmark used to keep worldgen overhead measurable. */
public final class WorldgenBenchmark {
	private static final Logger LOGGER = LogManager.getLogger();
	private static final String PROPERTY = "orespawn.worldgenBenchmarkMode";
	private static final String MODE = System.getProperty(PROPERTY, "").trim().toLowerCase(Locale.ROOT);
	private static final boolean ENABLED = "vanilla".equals(MODE) || "cyano".equals(MODE) || "sky".equals(MODE);
	private static final boolean SPRING_AUDIT_ENABLED =
			Boolean.getBoolean("orespawn.worldgenBenchmarkSpringAudit");
	private static final AtomicLong FLUID_DEPOSITS_PLACED = new AtomicLong();

	private WorldgenBenchmark() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static void register() {
		if (!ENABLED) {
			return;
		}
		ServerAboutToStartEvent.BUS.addListener(WorldgenBenchmark::onServerAboutToStart);
		ServerStartedEvent.BUS.addListener(WorldgenBenchmark::onServerStarted);
	}

	public static boolean isVanillaBaseline() {
		return ENABLED && "vanilla".equals(MODE);
	}

	static boolean isSpringAuditEnabled() {
		return SPRING_AUDIT_ENABLED;
	}

	static void recordFluidDeposit() {
		if (ENABLED && Boolean.getBoolean("orespawn.worldgenBenchmarkFluidAudit")) {
			FLUID_DEPOSITS_PLACED.incrementAndGet();
		}
	}

	private static void onServerAboutToStart(ServerAboutToStartEvent event) {
		if (isVanillaBaseline()) {
			return;
		}

		WorldGeologyProfile source = WorldGeologyProfileManager.activeProfile();
		GeologyMode geologyMode = "cyano".equals(MODE) ? GeologyMode.LEGACY : GeologyMode.GEOME;
		WorldGeologyProfile benchmarkProfile = source.withSelection(geologyMode,
				Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE, Preset.AVERAGE,
				source.placeFluidDeposits());
		if (Boolean.getBoolean("orespawn.worldgenBenchmarkVanillaOres")) {
			com.google.gson.JsonObject root = benchmarkProfile.rootCopy();
			root.addProperty("manage_vanilla_ores", true);
			benchmarkProfile = benchmarkProfile.withRoot(root);
		}
		WorldGeologyProfileManager.applyBenchmarkProfile(benchmarkProfile);
	}

	private static void onServerStarted(ServerStartedEvent event) {
		String dimensionName = System.getProperty("orespawn.worldgenBenchmarkDimension", "overworld")
				.trim().toLowerCase(Locale.ROOT);
		ServerLevel level = event.getServer().getLevel(benchmarkDimensionKey(dimensionName));
		if (level == null) {
			throw new IllegalStateException("Benchmark dimension is unavailable: " + dimensionName);
		}
		int radius = boundedInteger("orespawn.worldgenBenchmarkRadius", 4, 1, 16);
		int repetitions = boundedInteger("orespawn.worldgenBenchmarkRepetitions", 3, 1, 9);
		int warmupRadius = Math.min(2, radius);
		int chunks = squareDiameter(radius);
		int baseCenterX = integer("orespawn.worldgenBenchmarkCenterX", 256);
		int baseCenterZ = integer("orespawn.worldgenBenchmarkCenterZ", 256);
		int[] locatedCenter = locateBiomeType(level, baseCenterX, baseCenterZ);
		baseCenterX = locatedCenter[0];
		baseCenterZ = locatedCenter[1];
		int centerStep = integer("orespawn.worldgenBenchmarkCenterStep", 64);

		LOGGER.info("ORESPAWN_BENCHMARK start mode={} dimension={} seed={} target_chunks={} repetitions={} "
				+ "java={} processors={} max_heap_mb={}",
				MODE, level.dimension().identifier(), level.getSeed(), chunks, repetitions,
				System.getProperty("java.version"),
				Runtime.getRuntime().availableProcessors(), Runtime.getRuntime().maxMemory() / (1024L * 1024L));

		generateSquare(level, baseCenterX - 64, baseCenterZ - 64, warmupRadius);
		FLUID_DEPOSITS_PLACED.set(0L);
		long fluidDepositTotal = 0L;
		double[] milliseconds = new double[repetitions];
		for (int repetition = 0; repetition < repetitions; repetition++) {
			int centerX = baseCenterX + (repetition * centerStep);
			if (SPRING_AUDIT_ENABLED) {
				VanillaSpringCompatibility.beginSpringAudit();
			}
			long fluidDepositsBefore = FLUID_DEPOSITS_PLACED.get();
			long started = System.nanoTime();
			generateSquare(level, centerX, baseCenterZ, radius);
			milliseconds[repetition] = (System.nanoTime() - started) / 1_000_000.0D;
			LOGGER.info("ORESPAWN_BENCHMARK result mode={} repetition={} chunks={} elapsed_ms={} ms_per_chunk={}",
					MODE, repetition + 1, chunks, format(milliseconds[repetition]),
					format(milliseconds[repetition] / chunks));
			if (Boolean.getBoolean("orespawn.worldgenBenchmarkFluidAudit")) {
				long placed = FLUID_DEPOSITS_PLACED.get() - fluidDepositsBefore;
				fluidDepositTotal += placed;
				LOGGER.info("ORESPAWN_BENCHMARK_FLUID mode={} repetition={} successful_deposits={}",
						MODE, repetition + 1, placed);
			}
			if (Boolean.getBoolean("orespawn.worldgenBenchmarkOreAudit")) {
				auditOres(level, centerX, baseCenterZ, radius, repetition + 1);
			}
			if (SPRING_AUDIT_ENABLED) {
				auditSourceFluids(level, centerX, baseCenterZ, radius, repetition + 1);
			}
			auditBiomes(level, centerX, baseCenterZ, radius, repetition + 1);
		}

		double[] sorted = milliseconds.clone();
		Arrays.sort(sorted);
		double median = sorted[sorted.length / 2];
		LOGGER.info("ORESPAWN_BENCHMARK summary mode={} chunks_per_repetition={} repetitions={} "
				+ "median_ms={} median_ms_per_chunk={} min_ms={} max_ms={}",
				MODE, chunks, repetitions, format(median), format(median / chunks),
				format(sorted[0]), format(sorted[sorted.length - 1]));
		if (Boolean.getBoolean("orespawn.worldgenBenchmarkFluidAudit") && fluidDepositTotal == 0L) {
			throw new IllegalStateException("Benchmark fluid audit found no successful deposits");
		}
		if (Boolean.getBoolean("orespawn.worldgenBenchmarkStopServer")) {
			if (ownsServerShutdown(event.getServer().getClass())) {
				LOGGER.info("ORESPAWN_BENCHMARK stopping server after completed benchmark");
				event.getServer().halt(false);
			} else {
				LOGGER.info("ORESPAWN_BENCHMARK leaving shutdown to the GameTest harness");
			}
		}
	}

	private static void auditSourceFluids(ServerLevel level, int centerX, int centerZ,
			int radius, int repetition) {
		MessageDigest digest;
		try {
			digest = MessageDigest.getInstance("SHA-256");
		} catch (NoSuchAlgorithmException impossible) {
			throw new IllegalStateException("SHA-256 is unavailable", impossible);
		}
		List<SpringCoordinate> coordinates = isVanillaBaseline()
				? scanVanillaSpringShapes(level, centerX, centerZ, radius)
				: recordedVanillaSpringPlacements(centerX, centerZ, radius);
		coordinates.sort(Comparator.comparingInt(SpringCoordinate::x)
				.thenComparingInt(SpringCoordinate::z)
				.thenComparingInt(SpringCoordinate::y)
				.thenComparing(SpringCoordinate::fluid));
		long water = 0L;
		long lava = 0L;
		for (SpringCoordinate coordinate : coordinates) {
			if ("water".equals(coordinate.fluid())) water++;
			else if ("lava".equals(coordinate.fluid())) lava++;
			updateDigestInt(digest, coordinate.x());
			updateDigestInt(digest, coordinate.y());
			updateDigestInt(digest, coordinate.z());
			digest.update(coordinate.fluid().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			digest.update((byte) 0);
		}
		LOGGER.info("ORESPAWN_BENCHMARK_SPRING mode={} repetition={} water_candidates={} "
				+ "lava_candidates={} coordinate_sha256={}",
				MODE, repetition, water, lava,
				HexFormat.of().formatHex(digest.digest()).toUpperCase(Locale.ROOT));
		LOGGER.info("ORESPAWN_BENCHMARK_SPRING_COORDINATES mode={} repetition={} coordinates={}",
				MODE, repetition, coordinates);
	}

	private static List<SpringCoordinate> recordedVanillaSpringPlacements(
			int centerX, int centerZ, int radius) {
		int minimumX = (centerX - radius) << 4;
		int maximumX = ((centerX + radius + 1) << 4) - 1;
		int minimumZ = (centerZ - radius) << 4;
		int maximumZ = ((centerZ + radius + 1) << 4) - 1;
		List<SpringCoordinate> result = new ArrayList<>();
		for (VanillaSpringCompatibility.SpringAuditPlacement placement
				: VanillaSpringCompatibility.springAuditPlacements()) {
			if (placement.x() >= minimumX && placement.x() <= maximumX
					&& placement.z() >= minimumZ && placement.z() <= maximumZ) {
				result.add(new SpringCoordinate(placement.x(), placement.y(),
						placement.z(), placement.fluid()));
			}
		}
		return result;
	}

	private static List<SpringCoordinate> scanVanillaSpringShapes(ServerLevel level,
			int centerX, int centerZ, int radius) {
		List<SpringCoordinate> result = new ArrayList<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
			for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				int minX = chunk.getPos().getMinBlockX();
				int minZ = chunk.getPos().getMinBlockZ();
				int maximumYExclusive = chunk.getMinY() + chunk.getHeight();
				for (int localX = 0; localX < 16; localX++) {
					for (int localZ = 0; localZ < 16; localZ++) {
						for (int y = chunk.getMinY(); y < maximumYExclusive; y++) {
							cursor.set(minX + localX, y, minZ + localZ);
							var fluid = chunk.getBlockState(cursor).getFluidState();
							if (!fluid.isSource()) continue;
							boolean water = fluid.getType() == Fluids.WATER;
							boolean lava = fluid.getType() == Fluids.LAVA;
							if ((water || lava) && hasVanillaSpringShape(level, cursor, water)) {
								result.add(new SpringCoordinate(cursor.getX(), y, cursor.getZ(),
										water ? "water" : "lava"));
							}
						}
					}
				}
			}
		}
		return result;
	}

	/**
	 * Recognizes the post-placement shape used by the vanilla overworld spring
	 * features. The audit runs synchronously while the benchmark chunks are
	 * generated, before their scheduled fluid ticks can fill the one open side.
	 * This deliberately excludes oceans and aquifers from the coordinate digest.
	 */
	private static boolean hasVanillaSpringShape(ServerLevel level, BlockPos origin,
			boolean water) {
		if (!isVanillaSpringHost(level.getBlockState(origin.above()), water)
				|| !isVanillaSpringHost(level.getBlockState(origin.below()), water)) {
			return false;
		}
		int rocks = 0;
		int holes = 0;
		for (var direction : new net.minecraft.core.Direction[] {
				net.minecraft.core.Direction.WEST, net.minecraft.core.Direction.EAST,
				net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH,
				net.minecraft.core.Direction.DOWN }) {
			BlockState neighbor = level.getBlockState(origin.relative(direction));
			if (isVanillaSpringHost(neighbor, water)) rocks++;
			if (neighbor.isAir()) holes++;
		}
		return rocks == 4 && holes == 1;
	}

	private static boolean isVanillaSpringHost(BlockState state, boolean water) {
		Block block = state.getBlock();
		return block == Blocks.STONE || block == Blocks.GRANITE
				|| block == Blocks.DIORITE || block == Blocks.ANDESITE
				|| block == Blocks.DEEPSLATE || block == Blocks.TUFF
				|| block == Blocks.CALCITE || block == Blocks.DIRT
				|| water && (block == Blocks.SNOW_BLOCK || block == Blocks.POWDER_SNOW
						|| block == Blocks.PACKED_ICE);
	}

	private static void updateDigestInt(MessageDigest digest, int value) {
		digest.update((byte) (value >>> 24));
		digest.update((byte) (value >>> 16));
		digest.update((byte) (value >>> 8));
		digest.update((byte) value);
	}

	private record SpringCoordinate(int x, int y, int z, String fluid) {
	}

	static boolean ownsServerShutdown(Class<? extends MinecraftServer> serverType) {
		return !GameTestServer.class.isAssignableFrom(serverType);
	}

	static ResourceKey<Level> benchmarkDimensionKey(String configured) {
		String dimensionName = configured.trim().toLowerCase(Locale.ROOT);
		return switch (dimensionName) {
			case "overworld" -> Level.OVERWORLD;
			case "nether" -> Level.NETHER;
			case "end" -> Level.END;
			default -> {
				Identifier id = Identifier.tryParse(dimensionName);
				if (id == null) {
					throw new IllegalArgumentException("Invalid benchmark dimension: " + configured);
				}
				yield ResourceKey.create(Registries.DIMENSION, id);
			}
		};
	}

	private static void generateSquare(ServerLevel level, int centerX, int centerZ, int radius) {
		for (int z = centerZ - radius; z <= centerZ + radius; z++) {
			for (int x = centerX - radius; x <= centerX + radius; x++) {
				level.getChunk(x, z, ChunkStatus.FULL, true);
			}
		}
	}

	private static int squareDiameter(int radius) {
		int diameter = (radius * 2) + 1;
		return diameter * diameter;
	}

	private static void auditBiomes(ServerLevel level, int centerX, int centerZ, int radius,
			int repetition) {
		String configured = System.getProperty("orespawn.worldgenBenchmarkBiomeAudit", "").trim();
		if (configured.isEmpty()) return;
		Identifier expected = Identifier.tryParse(configured);
		if (expected == null) {
			throw new IllegalArgumentException("Invalid benchmark biome audit ID: " + configured);
		}
		int matching = 0;
		int total = squareDiameter(radius);
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
			for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
				cursor.set((chunkX << 4) + 8, level.getSeaLevel(), (chunkZ << 4) + 8);
				Identifier actual = level.getBiome(cursor).unwrapKey()
						.map(key -> key.identifier()).orElse(null);
				if (expected.equals(actual)) matching++;
			}
		}
		LOGGER.info("ORESPAWN_BENCHMARK_BIOME mode={} repetition={} expected={} matching={} total={}",
				MODE, repetition, expected, matching, total);
		if (matching != total) {
			throw new IllegalStateException("Benchmark biome audit expected " + expected
					+ " in all " + total + " chunks but matched " + matching);
		}
	}

	private static int[] locateBiomeType(ServerLevel level, int centerX, int centerZ) {
		String configured = System.getProperty("orespawn.worldgenBenchmarkBiomeType", "").trim();
		if (configured.isEmpty()) {
			return new int[] { centerX, centerZ };
		}
		BlockPos origin = new BlockPos(centerX << 4, level.getSeaLevel(), centerZ << 4);
		Pair<BlockPos, Holder<Biome>> located = level.findClosestBiome3d(holder -> holder.unwrapKey()
				.map(key -> BiomeTypeCompatibility.hasType(key, configured)).orElse(false),
				origin, 16384, 32, 64);
		if (located == null) {
			throw new IllegalStateException("Benchmark could not locate biome dictionary type " + configured);
		}
		int locatedX = located.getFirst().getX() >> 4;
		int locatedZ = located.getFirst().getZ() >> 4;
		LOGGER.info("ORESPAWN_BENCHMARK located biome_type={} center_chunk_x={} center_chunk_z={}",
				configured.toUpperCase(Locale.ROOT), locatedX, locatedZ);
		return new int[] { locatedX, locatedZ };
	}

	private static void auditOres(ServerLevel level, int centerX, int centerZ, int radius,
			int repetition) {
		Map<String, OreAudit> audits = new LinkedHashMap<>();
		if (net.minecraft.world.level.Level.NETHER.equals(level.dimension())) {
			audits.put("nether_gold", new OreAudit(Blocks.NETHER_GOLD_ORE, Blocks.NETHER_GOLD_ORE));
			audits.put("quartz", new OreAudit(Blocks.NETHER_QUARTZ_ORE, Blocks.NETHER_QUARTZ_ORE));
			audits.put("ancient_debris", new OreAudit(Blocks.ANCIENT_DEBRIS, Blocks.ANCIENT_DEBRIS));
		} else {
			audits.put("coal", new OreAudit(Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE));
			audits.put("copper", new OreAudit(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE));
			audits.put("iron", new OreAudit(Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE));
			audits.put("gold", new OreAudit(Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE));
			audits.put("redstone", new OreAudit(Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE));
			audits.put("diamond", new OreAudit(Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE));
			audits.put("lapis", new OreAudit(Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE));
			audits.put("emerald", new OreAudit(Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE));
		}
		addConfiguredAudits(audits);

		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
		for (int chunkZ = centerZ - radius; chunkZ <= centerZ + radius; chunkZ++) {
			for (int chunkX = centerX - radius; chunkX <= centerX + radius; chunkX++) {
				LevelChunk chunk = level.getChunk(chunkX, chunkZ);
				int minX = chunk.getPos().getMinBlockX();
				int minZ = chunk.getPos().getMinBlockZ();
				for (int localX = 0; localX < 16; localX++) {
					for (int localZ = 0; localZ < 16; localZ++) {
						cursor.set(minX + localX, chunk.getMinY(), minZ + localZ);
						int maximumYExclusive = chunk.getMinY() + chunk.getHeight();
						for (int y = chunk.getMinY(); y < maximumYExclusive; y++) {
							cursor.setY(y);
							BlockState state = chunk.getBlockState(cursor);
							for (OreAudit audit : audits.values()) {
								if (audit.accepts(state.getBlock())) {
									audit.record(localX, localZ, y,
											isAdjacentToAir(level, cursor.getX(), y, cursor.getZ(), neighbor));
								}
							}
						}
					}
				}
			}
		}

		int chunks = squareDiameter(radius);
		for (Map.Entry<String, OreAudit> entry : audits.entrySet()) {
			OreAudit audit = entry.getValue();
			LOGGER.info("ORESPAWN_BENCHMARK_ORE mode={} repetition={} ore={} total={} per_chunk={} "
					+ "min_y={} max_y={} out_of_range={} exposed={} exposed_pct={} "
					+ "x_low_pct={} x_high_pct={} z_low_pct={} z_high_pct={}",
					MODE, repetition, entry.getKey(), audit.total, format(audit.total / (double) chunks),
					audit.minimumObserved(), audit.maximumObserved(), audit.outOfRange,
					audit.exposed, format(audit.exposedPercent()),
					format(audit.edgePercent(audit.byX, 0)), format(audit.edgePercent(audit.byX, 14)),
					format(audit.edgePercent(audit.byZ, 0)), format(audit.edgePercent(audit.byZ, 14)));
			audit.assertExpectation(entry.getKey());
		}
	}

	private static void addConfiguredAudits(Map<String, OreAudit> audits) {
		String configured = System.getProperty("orespawn.worldgenBenchmarkBlockAudit", "").trim();
		if (configured.isEmpty()) {
			return;
		}
		for (String specification : configured.split(";")) {
			String[] fields = specification.trim().split(",");
			if (fields.length != 4) {
				throw new IllegalArgumentException("Invalid benchmark block audit specification: " + specification);
			}
			Identifier id = Identifier.tryParse(fields[0].trim());
			if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) {
				throw new IllegalArgumentException("Unknown benchmark audit block: " + fields[0].trim());
			}
			int minimumY = Integer.parseInt(fields[1].trim());
			int maximumY = Integer.parseInt(fields[2].trim());
			boolean required = switch (fields[3].trim().toLowerCase(Locale.ROOT)) {
				case "present" -> true;
				case "absent" -> false;
				default -> throw new IllegalArgumentException(
						"Benchmark audit expectation must be present or absent: " + specification);
			};
			audits.put(id.toString(), new OreAudit(BuiltInRegistries.BLOCK.getValue(id),
					BuiltInRegistries.BLOCK.getValue(id),
					minimumY, maximumY, required));
		}
	}

	private static boolean isAdjacentToAir(ServerLevel level, int x, int y, int z,
			BlockPos.MutableBlockPos cursor) {
		return level.getBlockState(cursor.set(x + 1, y, z)).isAir()
				|| level.getBlockState(cursor.set(x - 1, y, z)).isAir()
				|| level.getBlockState(cursor.set(x, y + 1, z)).isAir()
				|| level.getBlockState(cursor.set(x, y - 1, z)).isAir()
				|| level.getBlockState(cursor.set(x, y, z + 1)).isAir()
				|| level.getBlockState(cursor.set(x, y, z - 1)).isAir();
	}

	private static int boundedInteger(String property, int fallback, int min, int max) {
		try {
			return Math.max(min, Math.min(max, Integer.parseInt(System.getProperty(property, ""))));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static String format(double value) {
		return String.format(Locale.ROOT, "%.3f", value);
	}

	private static int integer(String property, int fallback) {
		try {
			return Integer.parseInt(System.getProperty(property, ""));
		} catch (NumberFormatException ignored) {
			return fallback;
		}
	}

	private static final class OreAudit {
		private final Block shallow;
		private final Block deep;
		private final int minimumY;
		private final int maximumY;
		private final Boolean required;
		private final long[] byX = new long[16];
		private final long[] byZ = new long[16];
		private long total;
		private long exposed;
		private long outOfRange;
		private int minimumObserved = Integer.MAX_VALUE;
		private int maximumObserved = Integer.MIN_VALUE;

		OreAudit(Block shallow, Block deep) {
			this(shallow, deep, Integer.MIN_VALUE, Integer.MAX_VALUE, null);
		}

		OreAudit(Block shallow, Block deep, int minimumY, int maximumY, Boolean required) {
			this.shallow = shallow;
			this.deep = deep;
			this.minimumY = minimumY;
			this.maximumY = maximumY;
			this.required = required;
		}

		boolean accepts(Block block) {
			return block == shallow || block == deep;
		}

		void record(int localX, int localZ, int y, boolean isExposed) {
			total++;
			byX[localX]++;
			byZ[localZ]++;
			minimumObserved = Math.min(minimumObserved, y);
			maximumObserved = Math.max(maximumObserved, y);
			if (y < minimumY || y > maximumY) outOfRange++;
			if (isExposed) exposed++;
		}

		String minimumObserved() {
			return total == 0L ? "n/a" : Integer.toString(minimumObserved);
		}

		String maximumObserved() {
			return total == 0L ? "n/a" : Integer.toString(maximumObserved);
		}

		void assertExpectation(String name) {
			if (outOfRange != 0L) {
				throw new IllegalStateException("Benchmark audit found " + outOfRange
						+ " out-of-range blocks for " + name + " (expected " + minimumY + ".." + maximumY + ")");
			}
			if (Boolean.TRUE.equals(required) && total == 0L) {
				throw new IllegalStateException("Benchmark audit found no blocks for required block " + name);
			}
			if (Boolean.FALSE.equals(required) && total != 0L) {
				throw new IllegalStateException("Benchmark audit found " + total + " blocks for absent block " + name);
			}
		}

		double exposedPercent() {
			return total == 0L ? 0.0D : (exposed * 100.0D) / total;
		}

		double edgePercent(long[] counts, int start) {
			double edgeAverage = (counts[start] + counts[start + 1]) / 2.0D;
			long interior = 0L;
			for (int i = 2; i < 14; i++) interior += counts[i];
			double interiorAverage = interior / 12.0D;
			return interiorAverage <= 0.0D ? 0.0D : (edgeAverage * 100.0D) / interiorAverage;
		}
	}
}
