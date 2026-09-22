package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

class BiomePaletteOrderingTest {
	@Test
	void ordinaryOrderIsStableAndUiOverridesAlwaysRunLast() {
		JsonObject section = new JsonObject();
		section.add("provider:first", new JsonObject());
		section.add("orespawn:ui/biome_overrides/minecraft_overworld", new JsonObject());
		section.add("provider:second", new JsonObject());
		List<String> ids = BiomeWorldgenManager.orderedPaletteEntries(section).stream()
				.map(Entry<String, JsonElement>::getKey).collect(Collectors.toList());
		assertEquals(java.util.Arrays.asList("provider:first", "provider:second",
				"orespawn:ui/biome_overrides/minecraft_overworld"), ids);
	}
}
