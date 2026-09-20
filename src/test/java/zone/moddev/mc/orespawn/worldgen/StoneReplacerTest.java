package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

class StoneReplacerTest {
	@Test
	void sharedMatchingStoneFeatureUsesOneImmutableWrapperAcrossBiomes() {
		PlacedFeature vanilla = new PlacedFeature(
				Holder.direct(StoneReplacer.FEATURE), Collections.emptyList());
		HolderOwner<PlacedFeature> owner = new HolderOwner<>() { };
		ResourceKey<PlacedFeature> key = ResourceKey.create(Registries.PLACED_FEATURE,
				Identifier.fromNamespaceAndPath("minecraft", "ore_granite_upper"));
		Holder.Reference<PlacedFeature> reference = Holder.Reference.createStandAlone(owner, key);
		reference.bindValue(vanilla);
		List<Holder<PlacedFeature>> firstBiome = new ArrayList<>(Arrays.asList(reference));
		List<Holder<PlacedFeature>> secondBiome = new ArrayList<>(Arrays.asList(reference));

		assertTrue(StoneReplacer.wrapVanillaMatchingStoneFeatures(firstBiome));
		assertTrue(StoneReplacer.wrapVanillaMatchingStoneFeatures(secondBiome));
		assertSame(firstBiome.get(0), secondBiome.get(0));
		assertSame(firstBiome.get(0).value(), secondBiome.get(0).value());
		assertSame(StoneReplacer.FEATURE, vanilla.feature().value());
	}

	@Test
	void ordinarySolidRocksCanUseTheSectionFastPath() {
		assertTrue(StoneReplacer.hasEquivalentHeightAndLightProperties(
				Blocks.STONE.defaultBlockState(), Blocks.GRANITE.defaultBlockState()));
	}

	@Test
	void heightOrLightChangingOutputsUseMinecraftsFullUpdatePath() {
		assertFalse(StoneReplacer.hasEquivalentHeightAndLightProperties(
				Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState()));
		assertFalse(StoneReplacer.hasEquivalentHeightAndLightProperties(
				Blocks.STONE.defaultBlockState(), Blocks.GLOWSTONE.defaultBlockState()));
	}

	@Test
	void oreOnlyProfilesKeepVanillaStoneFeatures() {
		assertFalse(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				Level.OVERWORLD, true, false));
	}

	@Test
	void configuredOverworldTerrainSuppressesMatchingVanillaFeatures() {
		assertTrue(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				Level.OVERWORLD, true, true));
	}

	@Test
	void netherAndEndAreNeverChanged() {
		assertFalse(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				Level.NETHER, true, true));
		assertFalse(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				Level.END, true, true));
	}

	@Test
	void explicitlyConfiguredCustomDimensionsCanSuppressMatchingStoneFeatures() {
		ResourceKey<Level> moon = ResourceKey.create(Registries.DIMENSION,
				Identifier.fromNamespaceAndPath("examplemod", "moon"));
		assertTrue(TerrainFeaturePolicy.shouldSuppressVanillaMatchingStoneFeature(
				moon, true, true));
	}

	@Test
	void invalidTerrainHostsRemainUnsafeEvenWhenDeclared() {
		LinkedHashSet<Block> hosts = new LinkedHashSet<>();
		hosts.add(Blocks.AIR);
		hosts.add(Blocks.WATER);
		hosts.add(Blocks.BEDROCK);
		hosts.add(Blocks.DIRT);
		BakedTerrainDimension terrain = new BakedTerrainDimension(
				ResourceKey.create(Registries.DIMENSION,
						Identifier.fromNamespaceAndPath("surfaceprobe", "the_end")),
				Collections.emptySet(), Collections.emptySet(), hosts);

		assertFalse(terrain.isReplaceable(Blocks.AIR.defaultBlockState()));
		assertFalse(terrain.isReplaceable(Blocks.WATER.defaultBlockState()));
		assertFalse(terrain.isReplaceable(Blocks.BEDROCK.defaultBlockState()));
		assertTrue(terrain.isReplaceable(Blocks.DIRT.defaultBlockState()));
	}
}
