package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.biome.FeatureSorter;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.Fluids;

class VanillaSpringCompatibilityTest {
	@Test
	void sharedVanillaSpringUsesOneWrapperAcrossBiomeLists() {
		SpringFeature waterSpring = new SpringFeature(
				Fluids.WATER.defaultFluidState(), true, 4, 1,
				HolderSet.direct(Blocks.STONE.builtInRegistryHolder()));
		SpringFeature lavaSpring = new SpringFeature(
				Fluids.LAVA.defaultFluidState(), true, 4, 1,
				HolderSet.direct(Blocks.STONE.builtInRegistryHolder()));
		PlacedFeature water = new PlacedFeature(Holder.direct(waterSpring), Collections.emptyList());
		PlacedFeature other = new PlacedFeature(Holder.direct(FlatBedrockFeature.FEATURE),
				Collections.emptyList());
		PlacedFeature lava = new PlacedFeature(Holder.direct(lavaSpring), Collections.emptyList());
		List<Holder<PlacedFeature>> firstBiome = new ArrayList<>(Arrays.asList(
				Holder.direct(water), Holder.direct(other), Holder.direct(lava)));
		List<Holder<PlacedFeature>> secondBiome = new ArrayList<>(Arrays.asList(
				Holder.direct(water), Holder.direct(lava)));
		List<FeatureSorter.StepFeatureData> vanillaSteps = FeatureSorter.buildFeaturesPerStep(
				Arrays.asList(firstBiome, secondBiome),
				features -> Collections.singletonList(HolderSet.direct(features)), false);

		assertTrue(VanillaSpringCompatibility.wrapFeatureList(firstBiome));
		assertTrue(VanillaSpringCompatibility.wrapFeatureList(secondBiome));
		assertSame(firstBiome.get(0), secondBiome.get(0));
		assertSame(firstBiome.get(0).value(), secondBiome.get(0).value());
		assertSame(firstBiome.get(2), secondBiome.get(1));
		assertSame(firstBiome.get(2).value(), secondBiome.get(1).value());

		List<FeatureSorter.StepFeatureData> wrappedSteps = FeatureSorter.buildFeaturesPerStep(
				Arrays.asList(firstBiome, secondBiome),
				features -> Collections.singletonList(HolderSet.direct(features)), false);
		FeatureSorter.StepFeatureData vanillaStep = vanillaSteps.get(0);
		FeatureSorter.StepFeatureData wrappedStep = wrappedSteps.get(0);
		assertEquals(vanillaStep.features().size(), wrappedStep.features().size());
		assertEquals(3, wrappedStep.features().size());
		for (int index = 0; index < vanillaStep.features().size(); index++) {
			PlacedFeature vanillaFeature = vanillaStep.features().get(index);
			PlacedFeature wrappedFeature = wrappedStep.features().get(index);
			assertEquals(index, vanillaStep.indexMapping().applyAsInt(vanillaFeature));
			assertEquals(index, wrappedStep.indexMapping().applyAsInt(wrappedFeature));
		}
		assertTrue(wrappedStep.features().get(0).feature().value()
				instanceof VanillaSpringCompatibility.SpringHostFeature);
		assertSame(other, wrappedStep.features().get(1));
		assertTrue(wrappedStep.features().get(2).feature().value()
				instanceof VanillaSpringCompatibility.SpringHostFeature);
	}

	@Test
	void configuredRocksExtendVanillaSpringHostsWithoutDuplicates() {
		HolderSet<Block> original = HolderSet.direct(
				Blocks.STONE.builtInRegistryHolder(),
				Blocks.DIRT.builtInRegistryHolder());

		HolderSet<Block> expanded = VanillaSpringCompatibility.merge(original,
				Arrays.asList(Blocks.STONE, Blocks.DIAMOND_BLOCK));

		assertEquals(3, expanded.size());
		assertTrue(expanded.contains(Blocks.STONE.builtInRegistryHolder()));
		assertTrue(expanded.contains(Blocks.DIRT.builtInRegistryHolder()));
		assertTrue(expanded.contains(Blocks.DIAMOND_BLOCK.builtInRegistryHolder()));
	}

	@Test
	void emptyTerrainProfileRestoresVanillaSpringHosts() {
		HolderSet<Block> original = HolderSet.direct(
				Blocks.STONE.builtInRegistryHolder(),
				Blocks.DIRT.builtInRegistryHolder());

		HolderSet<Block> restored =
				VanillaSpringCompatibility.merge(original, Collections.emptyList());

		assertEquals(2, restored.size());
		assertTrue(restored.contains(Blocks.STONE.builtInRegistryHolder()));
		assertTrue(restored.contains(Blocks.DIRT.builtInRegistryHolder()));
	}

	@Test
	void immutableSpringCopyRetainsTheOriginalHosts() {
		HolderSet<Block> original = HolderSet.direct(
				Blocks.STONE.builtInRegistryHolder(),
				Blocks.DIRT.builtInRegistryHolder());
		SpringFeature spring = new SpringFeature(
				Fluids.LAVA.defaultFluidState(), true, 4, 1, original);

		SpringFeature expanded = VanillaSpringCompatibility.withAdditionalHosts(spring,
				Collections.singleton(Blocks.DIAMOND_BLOCK));
		assertTrue(expanded.validBlocks().contains(Blocks.DIAMOND_BLOCK.builtInRegistryHolder()));
		assertEquals(original, spring.validBlocks());

		SpringFeature restored = VanillaSpringCompatibility.withAdditionalHosts(
				spring, Collections.emptyList());
		assertEquals(spring, restored);
	}
}
