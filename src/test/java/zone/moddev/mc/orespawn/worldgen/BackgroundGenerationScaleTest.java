package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.MinecraftTestBootstrap;

class BackgroundGenerationScaleTest {
	@BeforeAll
	static void bootstrap() throws Exception { MinecraftTestBootstrap.registerOrePatterns(); }

	@Test
	void controllerStaysUnscaledAndOnlyOrdinaryMatchingMaterialsAreReduced() throws Exception {
		assertEquals(8.0D, invoke("scaledManagedFrequency", new Class<?>[] {
				double.class, boolean.class, double.class }, 8.0D, true, 0.25D));
		assertEquals(2.0D, invoke("scaledManagedFrequency", new Class<?>[] {
				double.class, boolean.class, double.class }, 8.0D, false, 0.25D));
		Class<?>[] signature = { ResourceLocation.class, ResourceLocation.class, double.class, double.class };
		assertEquals(0.15D, invoke("channelBackgroundScale", signature,
				id("orespawn:iron"), id("orespawn:standard"), 0.15D, 0.75D));
		assertEquals(1.0D, invoke("channelBackgroundScale", signature,
				id("orespawn:iron"), id("depositprobe:district"), 0.15D, 0.75D));
		assertEquals(0.75D, invoke("channelBackgroundScale", signature,
				null, id("depositprobe:district"), 0.15D, 0.75D));
	}

	@Test
	void partialVanillaGateIsStableAndDoesNotConsumeRandomState() throws Exception {
		Class<?>[] signature = { ResourceLocation.class, ResourceLocation.class,
				long.class, int.class, int.class, double.class };
		assertEquals(false, invoke("passesBackgroundScale", signature,
				id("minecraft:overworld"), id("minecraft:iron_ore"), 42L, -3, 7, 0.0D));
		assertEquals(true, invoke("passesBackgroundScale", signature,
				id("minecraft:overworld"), id("minecraft:iron_ore"), 42L, -3, 7, 1.0D));
		int accepted = 0;
		for (int chunk = -5000; chunk < 5000; chunk++) {
			Object first = invoke("passesBackgroundScale", signature,
					id("minecraft:overworld"), id("minecraft:iron_ore"), 42L, chunk, -chunk, 0.25D);
			assertEquals(first, invoke("passesBackgroundScale", signature,
					id("minecraft:overworld"), id("minecraft:iron_ore"), 42L, chunk, -chunk, 0.25D));
			if ((boolean) first) accepted++;
		}
		assertTrue(accepted > 2200 && accepted < 2800, "quarter-scale acceptance: " + accepted);
	}

	@Test
	void bakeKeepsTheLowestEnabledControllerAndSeparatesDimensionsAndOreFamilies() throws Exception {
		JsonObject profile = new JsonObject();
		JsonObject ores = new JsonObject();
		ores.add("depositprobe:iron_a", controller("minecraft:iron_ore", 0.75D, true));
		ores.add("depositprobe:iron_b", controller("minecraft:deepslate_iron_ore", 0.25D, true));
		ores.add("depositprobe:disabled", controller("minecraft:iron_ore", 0.0D, false));
		profile.add("ores", ores);
		Method geology = GeomeConfig.class.getDeclaredMethod("bake", JsonObject.class, ResourceLocation.class);
		geology.setAccessible(true);
		Object config = geology.invoke(null, new JsonObject(), Level.OVERWORLD.location());
		Object baked = invoke("bakeOres", new Class<?>[] { JsonObject.class, BakedGeomeConfig.class }, profile, config);
		Map<?, ?> dimensions = (Map<?, ?>) field(baked, "backgroundGenerationScales");
		Map<?, ?> overworld = (Map<?, ?>) dimensions.get(Level.OVERWORLD);
		Object[] rules = (Object[]) ((Map<?, ?>) field(baked, "byDimension")).get(Level.OVERWORLD);
		assertEquals(2, rules.length);
		assertEquals(true, field(rules[0], "backgroundController"));
		assertEquals(0.25D, overworld.get(Blocks.IRON_ORE), "baked scales: " + overworld);
		assertFalse(overworld.containsKey(Blocks.GOLD_ORE));
		assertFalse(dimensions.containsKey(Level.NETHER));
		assertThrows(UnsupportedOperationException.class, overworld::clear);
	}

	@Test
	void disabledExplicitDimensionShadowsItsSelectorWithoutLeakingToTheNether() throws Exception {
		JsonObject ore = controller("minecraft:iron_ore", 0.5D, true);
		JsonObject dimensions = ore.getAsJsonObject("dimensions");
		JsonObject selector = dimensions.getAsJsonObject("minecraft:overworld").deepCopy();
		JsonObject disabled = new JsonObject();
		disabled.addProperty("enabled", false);
		dimensions.add("minecraft:overworld", disabled);
		JsonObject selectors = new JsonObject();
		selectors.add("orespawn:all_except_nether_end", selector);
		ore.add("dimension_selectors", selectors);
		JsonObject profile = new JsonObject();
		JsonObject ores = new JsonObject();
		ores.add("depositprobe:iron", ore);
		profile.add("ores", ores);
		Method geology = GeomeConfig.class.getDeclaredMethod("bake", JsonObject.class, ResourceLocation.class);
		geology.setAccessible(true);
		Object config = geology.invoke(null, new JsonObject(), Level.OVERWORLD.location());
		Object baked = invoke("bakeOres", new Class<?>[] { JsonObject.class, BakedGeomeConfig.class }, profile, config);
		Map<?, ?> explicit = (Map<?, ?>) field(baked, "backgroundGenerationScales");
		assertTrue(((Map<?, ?>) explicit.get(Level.OVERWORLD)).isEmpty());
		assertFalse(explicit.containsKey(Level.NETHER));
		assertEquals(0.5D, ((Map<?, ?>) field(baked, "selectorBackgroundGenerationScales")).get(Blocks.IRON_ORE));
		assertFalse(OreSpawnOreGeneration.selectorAllows(Level.NETHER));
		assertFalse(OreSpawnOreGeneration.selectorAllows(Level.END));
	}

	private static JsonObject controller(String output, double scale, boolean enabled) {
		JsonObject ore = new JsonObject();
		ore.addProperty("block", output);
		ore.addProperty("enabled", enabled);
		ore.addProperty("material", "orespawn:iron");
		JsonObject rule = new JsonObject();
		rule.addProperty("min_y", -32);
		rule.addProperty("max_y", 32);
		rule.addProperty("frequency", 8);
		rule.addProperty("quantity", 8);
		rule.addProperty("background_generation_scale", scale);
		JsonArray hosts = new JsonArray();
		hosts.add("minecraft:stone");
		rule.add("host_blocks", hosts);
		JsonObject dimensions = new JsonObject();
		dimensions.add("minecraft:overworld", rule);
		ore.add("dimensions", dimensions);
		return ore;
	}

	private static Object invoke(String name, Class<?>[] signature, Object... arguments) throws Exception {
		Method method = OreSpawnOreGeneration.class.getDeclaredMethod(name, signature);
		method.setAccessible(true);
		return method.invoke(null, arguments);
	}

	private static Object field(Object owner, String name) throws Exception {
		Field field = owner.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(owner);
	}

	private static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
}
