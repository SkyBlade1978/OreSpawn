package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;

import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceCandidate;
import zone.moddev.mc.orespawn.client.GeologyEditorSession.OreSourceGroup;

class OreSourcePlacementScreenTest {
	@Test
	void placementRulesExcludeExternalMissingAndOutputOnlyCandidates() {
		OreSourceCandidate managed = candidate("managed", "orespawn:standard", true, true, false);
		OreSourceCandidate second = candidate("second", "orespawn:standard", true, true, false);
		OreSourceCandidate external = candidate("external", "orespawn:standard", true, true, true);
		OreSourceCandidate missing = candidate("missing", "orespawn:standard", false, true, false);
		OreSourceCandidate outputOnly = candidate("output", "realisticdeposits:district", true, false, false);
		OreSourceGroup group = group("consolidated",
				Arrays.asList(managed, second, external, missing, outputOnly),
				Collections.singletonMap("lost:channel", "gone"));

		List<OreSourceCandidate> standard = OreSourceListScreen.placementCandidates(
				group, "orespawn:standard");
		assertEquals(Arrays.asList(managed, second), standard);
		assertTrue(OreSourceGroupSettingsScreen.placementSelectable(group, "orespawn:standard"));
		assertFalse(OreSourceGroupSettingsScreen.placementSelectable(group,
				"realisticdeposits:district"));
		assertEquals(Arrays.asList("lost:channel", "orespawn:standard"),
				OreSourceGroupSettingsScreen.placementChannels(group));
	}

	@Test
	void keepOriginalAndSingleManagedSourcesRemainReadOnly() {
		OreSourceCandidate first = candidate("first", "orespawn:standard", true, true, false);
		OreSourceCandidate second = candidate("second", "orespawn:standard", true, true, false);
		assertFalse(OreSourceGroupSettingsScreen.placementSelectable(
				group("consolidated", Collections.singletonList(first), Collections.emptyMap()),
				"orespawn:standard"));
		assertFalse(OreSourceGroupSettingsScreen.placementSelectable(
				group("keep_separate", Arrays.asList(first, second), Collections.emptyMap()),
				"orespawn:standard"));
		assertEquals(0xFFFF55, OreSourceGroupSettingsScreen.placementStatusColor(
				group("consolidated", Collections.singletonList(first), Collections.emptyMap()),
				Collections.singletonList("orespawn:standard")));
		assertEquals(0x55FF55, OreSourceGroupSettingsScreen.placementStatusColor(
				group("consolidated", Arrays.asList(first, second), Collections.emptyMap()),
				Collections.singletonList("orespawn:standard")));
		assertEquals(0xA0A0A0, OreSourceGroupSettingsScreen.placementStatusColor(
				group("keep_separate", Arrays.asList(first, second), Collections.emptyMap()),
				Collections.singletonList("orespawn:standard")));
	}

	private static OreSourceCandidate candidate(String source, String channel,
			boolean loaded, boolean active, boolean external) {
		return new OreSourceCandidate(source, "owner", "Owner", "1", "owner:ore", 0,
				channel, Collections.singletonList("oreTest"), loaded, active,
				external, false, false);
	}

	private static OreSourceGroup group(String mode, List<OreSourceCandidate> candidates,
			java.util.Map<String, String> placements) {
		return new OreSourceGroup("orespawn:test|minecraft:overworld", "orespawn:test",
				"minecraft:overworld", "Test", Collections.singletonList("oreTest"), false,
				mode, "balanced", "consolidated", candidates,
				Collections.unmodifiableMap(new LinkedHashMap<>()),
				Collections.unmodifiableMap(new LinkedHashMap<>(placements)));
	}
}
