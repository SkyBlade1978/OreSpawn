package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class DepositExtensionApiTest {

	@Test
	void providerCanKeepNewDefinitionsOutOfExistingWorlds() throws Exception {
		WorldgenProvider.Builder builder = provider();
		Method setting = builder.getClass().getMethod("mergeNewEntriesIntoExistingWorlds", boolean.class);
		setting.invoke(builder, false);
		WorldgenProvider provider = builder.build();
		assertFalse(provider.toJson().get("merge_new_entries_into_existing_worlds").getAsBoolean());
		assertEquals(false, provider.getClass().getMethod("mergeNewEntriesIntoExistingWorlds").invoke(provider));
		setting.invoke(builder, true);
		assertFalse(provider.toJson().get("merge_new_entries_into_existing_worlds").getAsBoolean(),
				"the built provider must not change with its builder");
	}

	@Test
	void backgroundScaleRoundTripsAndRejectsInvalidValues() throws Exception {
		var builder = WorldgenProvider.OreDimensionDefinition.builder(id("minecraft:overworld"))
				.hostBlock(id("minecraft:stone"));
		Method setting = builder.getClass().getMethod("backgroundGenerationScale", double.class);
		for (double scale : new double[] { 0.0D, 0.15D, 1.0D }) {
			setting.invoke(builder, scale);
			JsonObject rule = builder.build().toJson();
			assertEquals(scale, rule.get("background_generation_scale").getAsDouble());
		}
		for (double scale : new double[] { -0.1D, 1.1D, Double.NaN, Double.POSITIVE_INFINITY }) {
			setting.invoke(builder, scale);
			assertThrows(IllegalStateException.class, builder::build);
		}
	}

	@Test
	void oldBuilderDefaultsRemainUnchanged() throws Exception {
		var builder = WorldgenProvider.OreDimensionDefinition.builder(id("minecraft:overworld"))
				.hostBlock(id("minecraft:stone"));
		assertFalse(builder.build().toJson().has("background_generation_scale"));
		WorldgenProvider provider = provider().build();
		assertEquals(true, provider.getClass().getMethod("mergeNewEntriesIntoExistingWorlds").invoke(provider));
	}

	private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }

	private static WorldgenProvider.Builder provider() {
		return WorldgenProvider.builder("depositprobe", 1)
				.rock(id("depositprobe:slate"), id("minecraft:stone"), GeologyFamily.METAMORPHIC, rock -> {});
	}
}
