package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.Test;

import net.minecraft.core.HolderSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import net.minecraft.world.level.material.Fluids;

class VanillaSpringCompatibilityTest {
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
