package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.*;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ExactNativeHostApiTest {
	@Test
	void plainStoneDoesNotSelectOtherFlattenedRockIds() {
		var rule = WorldgenProvider.OreDimensionDefinition.builder(id("minecraft:overworld"))
				.hostBlock(id("minecraft:stone")).build();
		assertEquals("[\"minecraft:stone\"]", rule.toJson().get("host_blocks").toString());
		assertFalse(rule.hostBlocks().contains(id("minecraft:granite")));
	}

	@Test
	void rejectedBlockAndTagWeightsDoNotLeaveAnAccidentalHost() {
		for (double weight : new double[] { -0.1D, 1.1D, Double.NaN, Double.POSITIVE_INFINITY }) {
			var blocks = WorldgenProvider.OreDimensionDefinition.builder(id("minecraft:overworld"));
			assertThrows(IllegalArgumentException.class, () -> blocks.hostBlock(id("minecraft:stone"), weight));
			assertThrows(IllegalStateException.class, blocks::build);
			var tags = WorldgenProvider.OreDimensionDefinition.builder(id("minecraft:overworld"));
			assertThrows(IllegalArgumentException.class, () -> tags.hostTag(id("minecraft:stone_ore_replaceables"), weight));
			assertThrows(IllegalStateException.class, tags::build);
		}
	}

	private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
