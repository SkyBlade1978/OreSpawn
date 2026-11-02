package zone.moddev.mc.orespawn.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProviderWorldMergeTest {

	@Test
	void rejectsAnInvalidExistingWorldMergeFlag() {
		JsonObject provider = provider(false);
		provider.addProperty("merge_new_entries_into_existing_worlds", "false");
		assertThrows(JsonSyntaxException.class,
				() -> WorldgenIntegrationManager.validateProvider("depositprobe", provider));
	}

	@Test
	void freshWorldReceivesRulesButExistingWorldHonoursTheOptOut() throws Exception {
		withProvider(provider(false), () -> {
			JsonObject fresh = new JsonObject();
			assertTrue(WorldgenIntegrationManager.mergeProviderDefinitions(fresh));
			assertTrue(fresh.getAsJsonObject("rocks").has("depositprobe:slate"));
			JsonObject existing = new JsonObject();
			mergeExisting(existing);
			assertFalse(existing.getAsJsonObject("rocks").has("depositprobe:slate"));
			assertFalse(existing.has("merge_probe_default"));
			assertFalse(existing.getAsJsonObject("providers").getAsJsonObject("depositprobe")
					.getAsJsonArray("known_rocks").toString().contains("depositprobe:slate"));
		});
	}

	@Test
	void defaultMergePreservesWorldEditsAndDeletedEntryTombstones() throws Exception {
		JsonObject provider = provider(true);
		provider.remove("merge_new_entries_into_existing_worlds");
		withProvider(provider, () -> {
			JsonObject existing = new JsonObject();
			mergeExisting(existing);
			JsonObject rock = existing.getAsJsonObject("rocks").getAsJsonObject("depositprobe:slate");
			rock.addProperty("enabled", false);
			rock.addProperty("weight", 0.25D);
			mergeExisting(existing);
			assertFalse(rock.get("enabled").getAsBoolean());
			assertEquals(0.25D, rock.get("weight").getAsDouble());
			existing.getAsJsonObject("rocks").remove("depositprobe:slate");
			mergeExisting(existing);
			assertFalse(existing.getAsJsonObject("rocks").has("depositprobe:slate"));
		});
	}

	@Test
	void optedOutProviderRetainsExistingEntriesAndClearsOrphanStatus() throws Exception {
		withProvider(provider(false), () -> {
			JsonObject existing = new JsonObject();
			JsonObject rocks = new JsonObject();
			JsonObject saved = new JsonObject();
			saved.addProperty("weight", 0.25D);
			saved.addProperty("orphaned_provider", true);
			rocks.add("depositprobe:slate", saved);
			existing.add("rocks", rocks);
			mergeExisting(existing);
			assertEquals(0.25D, saved.get("weight").getAsDouble());
			assertFalse(saved.has("orphaned_provider"));
		});
	}

	private static void mergeExisting(JsonObject root) throws Exception {
		Method method = WorldgenIntegrationManager.class.getMethod(
				"mergeProviderDefinitionsIntoExistingWorld", JsonObject.class);
		method.invoke(null, root);
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	private static void withProvider(JsonObject provider, CheckedRunnable action) throws Exception {
		Field field = WorldgenIntegrationManager.class.getDeclaredField("ACTIVE_PROVIDERS");
		field.setAccessible(true);
		Map active = (Map) field.get(null);
		Map previous = new LinkedHashMap(active);
		Class<?> type = Class.forName(WorldgenIntegrationManager.class.getName() + "$ProviderDefinition");
		Constructor<?> constructor = type.getDeclaredConstructor(String.class, int.class, JsonObject.class);
		constructor.setAccessible(true);
		try {
			active.clear();
			active.put("depositprobe", constructor.newInstance("depositprobe", 1, provider));
			action.run();
		} finally {
			active.clear();
			active.putAll(previous);
		}
	}

	private static JsonObject provider(boolean merge) {
		JsonObject provider = new JsonObject();
		provider.addProperty("schema_version", 5);
		provider.addProperty("provider_modid", "depositprobe");
		provider.addProperty("provider_revision", 1);
		provider.addProperty("merge_new_entries_into_existing_worlds", merge);
		JsonObject rock = new JsonObject();
		rock.addProperty("enabled", true);
		rock.addProperty("block", "minecraft:stone");
		rock.addProperty("family", "metamorphic");
		rock.addProperty("min_y", -64);
		rock.addProperty("max_y", 319);
		rock.addProperty("depth_peak", 16);
		rock.addProperty("depth_spread", 32);
		rock.addProperty("weight", 1);
		rock.addProperty("ore_replaceable", true);
		JsonObject rocks = new JsonObject();
		rocks.add("depositprobe:slate", rock);
		provider.add("rocks", rocks);
		JsonObject defaults = new JsonObject();
		defaults.addProperty("merge_probe_default", true);
		provider.add("profile_defaults", defaults);
		return provider;
	}

	private interface CheckedRunnable { void run() throws Exception; }
}
