package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.OreFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockMatchTest;

class Minecraft263WorldgenContractTest {
	@Test
	void featureTypesUseMapCodecsAndFeatureResourcesUseThe263Path() throws Exception {
		assertNotNull(StoneReplacer.CODEC);
		assertNotNull(OreSpawnOreGeneration.CODEC);
		assertNotNull(FluidDepositFeature.CODEC);
		assertNotNull(FlatBedrockFeature.CODEC);
		assertNotNull(BiomeSurfaceFeature.CODEC);
		assertNotNull(VanillaSpringCompatibility.codec());
		assertTrue(Registries.elementsDirPath(Registries.FEATURE)
				.endsWith("worldgen/feature"));

		Path featureDirectory = Paths.get("src", "biomeIntegrationTest", "resources",
				"data", "surfaceprobe", "worldgen", "feature");
		assertTrue(Files.isDirectory(featureDirectory));
		assertFalse(Files.exists(featureDirectory.resolveSibling("configured_feature")));
	}

	@Test
	void sharedOreFeatureUsesOneImmutableWrapperAcrossBiomes() {
		OreFeature ore = new OreFeature(new BlockMatchTest(Blocks.STONE),
				Blocks.DIAMOND_ORE.defaultBlockState(), 8);
		PlacedFeature vanilla = new PlacedFeature(Holder.direct(ore), Collections.emptyList());
		List<Holder<PlacedFeature>> firstBiome = new ArrayList<>(
				Collections.singletonList(Holder.direct(vanilla)));
		List<Holder<PlacedFeature>> secondBiome = new ArrayList<>(
				Collections.singletonList(Holder.direct(vanilla)));

		assertTrue(VanillaOreFeatureGate.wrapFeatureList(firstBiome));
		assertTrue(VanillaOreFeatureGate.wrapFeatureList(secondBiome));
		assertSame(firstBiome.get(0), secondBiome.get(0));
		assertSame(firstBiome.get(0).value(), secondBiome.get(0).value());
		assertSame(ore, vanilla.feature().value());
	}

	@Test
	void fluidDepositsUseNormalChunkWritesFor263Bookkeeping() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java",
				"zone", "moddev", "mc", "orespawn", "worldgen",
				"FluidDepositFeature.java")), StandardCharsets.UTF_8);
		assertTrue(source.contains("chunk.setBlockState("));
		assertFalse(source.contains("LevelChunkSection"));
		assertFalse(source.contains("section.setBlockState("));
	}
}
