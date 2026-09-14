package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class OreSourcePoliciesTest {
	@BeforeAll
	static void bootstrapVanilla() {
		Forge12TestBootstrap.registerVanilla();
	}

	@Test
	void exactOreDictionaryNamesAndCuratedSpellingsProduceCanonicalMaterials() {
		OreSourcePolicies.Inference sulfur = OreSourcePolicies.inferMaterial(
				Arrays.asList("oreSulphur", "oreSulfur", "dustSulfur"));
		OreSourcePolicies.Inference aluminum = OreSourcePolicies.inferMaterial(
				Arrays.asList("oreAluminium", "oreAluminum"));

		assertEquals("orespawn:sulfur", sulfur.material.toString());
		assertEquals(Arrays.asList("oreSulfur", "oreSulphur"), sulfur.names);
		assertFalse(sulfur.reviewRequired);
		assertEquals("orespawn:aluminum", aluminum.material.toString());
	}

	@Test
	void niterAndSaltpeterRemainDistinctAndAmbiguousMembershipRequiresReview() {
		assertEquals("orespawn:niter", OreSourcePolicies.inferMaterial(
				Collections.singletonList("oreNiter")).material.toString());
		assertEquals("orespawn:saltpeter", OreSourcePolicies.inferMaterial(
				Collections.singletonList("oreSaltpeter")).material.toString());
		OreSourcePolicies.Inference ambiguous = OreSourcePolicies.inferMaterial(
				Arrays.asList("oreNiter", "oreSaltpeter"));
		assertEquals("orespawn:review_required", ambiguous.material.toString());
		assertTrue(ambiguous.reviewRequired);
		assertEquals(null, OreSourcePolicies.inferMaterial(
				Arrays.asList("dustSulfur", "crushedSulfur")).material);
		OreSourcePolicies.Inference invalid = OreSourcePolicies.inferMaterial(
				Collections.singletonList("oreBad Path"));
		assertEquals("orespawn:review_required", invalid.material.toString());
		assertTrue(invalid.reviewRequired);
	}

	@Test
	void freshCataloguedMmdConflictConsolidatesUsingReviewedPriority() {
		JsonObject root = rootWithSulfurConflict();
		assertTrue(OreSourcePolicies.initialize(root, false));
		JsonObject policy = policy(root);

		assertEquals("consolidated", policy.get("mode").getAsString());
		assertEquals("consolidated", policy.get("status").getAsString());
		assertEquals(1, policy.getAsJsonObject("outputs").entrySet().size());
		assertTrue(policy.getAsJsonObject("outputs").has("mineralogy:sulfur"));
		assertEquals("mineralogy:sulfur", policy.getAsJsonObject("placement_sources")
				.get("orespawn:standard").getAsString());
	}

	@Test
	void upgradedWorldConflictPreservesEverySourceUntilUserChooses() {
		JsonObject root = rootWithSulfurConflict();
		OreSourcePolicies.initialize(root, true);
		JsonObject policy = policy(root);

		assertEquals("keep_separate", policy.get("mode").getAsString());
		assertEquals(2, policy.getAsJsonObject("outputs").entrySet().size());
	}

	@Test
	void thirdPartyConflictIsNotAutomaticallyConsolidated() {
		JsonObject root = root();
		addOre(root, "otherone:sulfur", "otherone", "minecraft:iron_ore", "minecraft:overworld");
		addOre(root, "othertwo:sulfur", "othertwo", "minecraft:gold_ore", "minecraft:overworld");
		OreSourcePolicies.initialize(root, false);

		JsonObject policy = policy(root);
		assertEquals("keep_separate", policy.get("mode").getAsString());
		assertEquals("review_required", policy.get("status").getAsString());
	}

	@Test
	void missingSelectionsRemainPersistedAndSnapshotsAreImmutable() {
		JsonObject root = rootWithSulfurConflict();
		OreSourcePolicies.initialize(root, false);
		root.getAsJsonObject("ores").remove("mineralogy:sulfur");
		OreSourcePolicies.initialize(root, false);

		JsonObject policy = policy(root);
		assertTrue(policy.getAsJsonObject("outputs").has("mineralogy:sulfur"));
		assertEquals("missing_source", policy.get("status").getAsString());
		boolean missing = false;
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			JsonObject candidate = element.getAsJsonObject();
			if ("mineralogy:sulfur".equals(candidate.get("source_id").getAsString())) {
				missing = !candidate.get("loaded").getAsBoolean();
			}
		}
		assertTrue(missing);
		OreSourcePolicies.Snapshot snapshot = OreSourcePolicies.snapshot(root);
		assertThrows(UnsupportedOperationException.class, () -> snapshot.groups().clear());
		assertThrows(UnsupportedOperationException.class,
				() -> snapshot.groups().get(0).outputs.put("replacement", 2.0D));

		addOre(root, "mineralogy:sulfur", "mineralogy", "minecraft:iron_ore",
				"minecraft:overworld");
		OreSourcePolicies.initialize(root, false);
		for (JsonElement element : policy(root).getAsJsonArray("candidates")) {
			JsonObject candidate = element.getAsJsonObject();
			if ("mineralogy:sulfur".equals(candidate.get("source_id").getAsString())) {
				assertTrue(candidate.get("loaded").getAsBoolean());
			}
		}
	}

	@Test
	void lithiumUsesItsReviewedMmdPriority() {
		JsonObject root = root();
		addOre(root, "electricadvantage:lithium", "electricadvantage",
				"minecraft:gold_ore", "orespawn:lithium", "minecraft:overworld", null);
		addOre(root, "baseminerals:lithium", "baseminerals",
				"minecraft:iron_ore", "orespawn:lithium", "minecraft:overworld", null);
		OreSourcePolicies.initialize(root, false);
		JsonObject policy = policy(root);
		assertEquals("consolidated", policy.get("mode").getAsString());
		assertTrue(policy.getAsJsonObject("outputs").has("baseminerals:lithium"));
	}

	@Test
	void placementChannelsStayIndependentWhileOutputsRemainMaterialWide() {
		JsonObject root = rootWithSulfurConflict();
		addOre(root, "electricadvantage:sulfur_district", "electricadvantage",
				"minecraft:coal_ore", "orespawn:sulfur", "minecraft:overworld",
				"realisticdeposits:district");
		OreSourcePolicies.initialize(root, false);
		JsonObject policy = policy(root);
		assertEquals(2, policy.getAsJsonObject("placement_sources").entrySet().size());
		assertEquals("mineralogy:sulfur", policy.getAsJsonObject("placement_sources")
				.get("orespawn:standard").getAsString());
		assertEquals("electricadvantage:sulfur_district", policy.getAsJsonObject("placement_sources")
				.get("realisticdeposits:district").getAsString());
		assertEquals(1, policy.getAsJsonObject("outputs").entrySet().size());
	}

	@Test
	void catalogExcludesScienceAndSeparatesDenseEnrichment() {
		JsonObject root = root();
		addOre(root, "mineralogy:sulfur", "mineralogy", "minecraft:iron_ore",
				"minecraft:overworld");
		addOre(root, "densemetals:dense_sulfur", "densemetals", "minecraft:gold_ore",
				"minecraft:overworld");
		addOre(root, "basesciences:sulfur", "basesciences", "minecraft:coal_ore",
				"minecraft:overworld");
		OreSourcePolicies.initialize(root, false);
		JsonObject policy = policy(root);
		assertEquals("keep_separate", policy.get("mode").getAsString());
		assertEquals(2, policy.getAsJsonArray("candidates").size());
		assertFalse(policy.getAsJsonObject("outputs").has("densemetals:dense_sulfur"));
		boolean enrichment = false;
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			JsonObject candidate = element.getAsJsonObject();
			enrichment |= "densemetals:dense_sulfur".equals(
					candidate.get("source_id").getAsString())
					&& candidate.get("enrichment").getAsBoolean();
		}
		assertTrue(enrichment);
	}

	@Test
	void netherAndEndCatalogCandidatesCannotLeakIntoOtherDomains() {
		JsonObject root = root();
		addOre(root, "nethermetals:sulfur", "nethermetals", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		addOre(root, "endmetals:sulfur", "endmetals", "minecraft:coal_ore",
				"orespawn:sulfur", "minecraft:overworld", null);
		OreSourcePolicies.initialize(root, false);
		assertTrue(root.getAsJsonObject(OreSourcePolicies.SECTION).entrySet().isEmpty());

		addOre(root, "nethermetals:sulfur", "nethermetals", "minecraft:gold_ore",
				"orespawn:sulfur", "minecraft:the_nether", null);
		addOre(root, "baseminerals:sulfur", "baseminerals", "minecraft:iron_ore",
				"orespawn:sulfur", "minecraft:the_nether", null);
		OreSourcePolicies.initialize(root, false);
		JsonObject policy = policy(root);
		assertEquals("minecraft:the_nether", policy.get("domain").getAsString());
	}

	@Test
	void customPatternsDefaultToTheirOwnPlacementChannel() {
		JsonObject custom = new JsonObject();
		JsonObject pattern = new JsonObject();
		pattern.addProperty("type", "realisticdeposits:district");
		pattern.add("settings", new JsonObject());
		custom.add("pattern", pattern);
		assertEquals("realisticdeposits:district",
				OreSourcePolicies.placementChannel(custom).toString());
		pattern.addProperty("type", "orespawn:registered_custom");
		assertEquals("orespawn:registered_custom",
				OreSourcePolicies.placementChannel(custom).toString());

		JsonObject builtIn = new JsonObject();
		builtIn.addProperty("pattern", "vein");
		assertEquals("orespawn:standard", OreSourcePolicies.placementChannel(builtIn).toString());
	}

	private static JsonObject rootWithSulfurConflict() {
		JsonObject root = root();
		addOre(root, "mineralogy:sulfur", "mineralogy", "minecraft:iron_ore", "minecraft:overworld");
		addOre(root, "baseminerals:sulfur", "baseminerals", "minecraft:gold_ore", "minecraft:overworld");
		return root;
	}

	private static JsonObject root() {
		JsonObject root = new JsonObject();
		root.add("ores", new JsonObject());
		return root;
	}

	private static void addOre(JsonObject root, String id, String owner, String block, String dimension) {
		addOre(root, id, owner, block, "orespawn:sulfur", dimension, null);
	}

	private static void addOre(JsonObject root, String id, String owner, String block,
			String material, String dimension, String channel) {
		JsonObject ore = new JsonObject();
		ore.addProperty("enabled", true);
		ore.addProperty("block", block);
		ore.addProperty("material", material);
		ore.addProperty("source_provider", owner);
		JsonObject dimensions = new JsonObject();
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("pattern", "vein");
		if (channel != null) rule.addProperty("placement_channel", channel);
		dimensions.add(dimension, rule);
		ore.add("dimensions", dimensions);
		root.getAsJsonObject("ores").add(id, ore);
	}

	private static JsonObject policy(JsonObject root) {
		JsonObject policies = root.getAsJsonObject(OreSourcePolicies.SECTION);
		assertEquals(1, policies.entrySet().size());
		return policies.entrySet().iterator().next().getValue().getAsJsonObject();
	}
}
