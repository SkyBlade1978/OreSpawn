package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

import zone.moddev.mc.orespawn.test.Forge12TestBootstrap;

class GeologyEditorSessionTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		Forge12TestBootstrap.registerVanilla();
		ItemStack diamond = new ItemStack(Blocks.DIAMOND_ORE);
		boolean registered = false;
		for (int id : OreDictionary.getOreIDs(diamond)) {
			registered |= "oreDiamond".equals(OreDictionary.getOreName(id));
		}
		if (!registered) OreDictionary.registerOre("oreDiamond", diamond);
	}

	@Test
	void emptyStandaloneProfileIsValidAndFirstRockActivatesOverworldTerrain() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		assertFalse(session.hasTerrainRules());
		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());

		session.assignRock("minecraft:stone", zone.moddev.mc.orespawn.worldgen.RockFamily.SEDIMENTARY);
		assertTrue(session.hasTerrainRules());
		JsonObject overworld = session.section("terrain_dimensions")
				.getAsJsonObject("minecraft:overworld");
		assertTrue(overworld.getAsJsonArray("host_blocks").toString().contains("minecraft:stone"));
		assertFalse(overworld.getAsJsonArray("host_blocks").toString().contains("minecraft:deepslate"));
	}

	@Test
	void firstUseStrataStartsWithBalancedVanillaRocks() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));

		assertTrue(session.configureDefaultVanillaStrata());
		assertTrue(session.hasTerrainRules());
		assertEquals(4, session.section("rocks").entrySet().size());
		assertFalse(session.section("rocks").has("minecraft:calcite"));
		assertFalse(session.section("rocks").has("minecraft:dripstone_block"));
		assertEquals("sedimentary", session.rock("minecraft:stone").get("family").getAsString());
		assertEquals("igneous_intrusive", session.rock("orespawn:vanilla_granite").get("family").getAsString());
		assertEquals(1, session.rock("orespawn:vanilla_granite").get("metadata").getAsInt());
		assertEquals("igneous_volcanic", session.rock("orespawn:vanilla_andesite").get("family").getAsString());
		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());
	}

	@Test
	void firstUseStrataDoesNotOverwriteAnExistingRockChoice() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		session.assignRock("minecraft:coal_block",
				zone.moddev.mc.orespawn.worldgen.RockFamily.SEDIMENTARY);

		assertFalse(session.configureDefaultVanillaStrata());
		assertEquals(1, session.section("rocks").entrySet().size());
		assertTrue(session.section("rocks").has("minecraft:coal_block"));
	}

	@Test
	void availableDimensionsPutVanillaFirstAndIncludeInstalledAndConfiguredIds() {
		GeologyEditorSession session = new GeologyEditorSession(
				WorldGeologyProfile.recommended(false),
				Arrays.asList("zeta:moon", "alpha:void", "not a valid id"));

		JsonObject dimensions = new JsonObject();
		dimensions.add("example:caverns", new JsonObject());
		session.ore("example:test_ore").add("dimensions", dimensions);

		assertEquals(Arrays.asList(
				"minecraft:overworld",
				"minecraft:the_nether",
				"minecraft:the_end",
				"alpha:void",
				"example:caverns",
				"zeta:moon"), session.availableDimensionIds());
	}

	@Test
	void providerFluidRemovalLeavesATombstoneButLocalRulesAreDeleted() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		JsonObject providerRule = new JsonObject();
		providerRule.addProperty("source_provider", "examplemod");
		providerRule.addProperty("enabled", true);
		session.section("fluid_deposits").add("examplemod:fluid_deposit/brine", providerRule);
		JsonObject localRule = new JsonObject();
		session.section("fluid_deposits").add("pack:fluid_deposit/test", localRule);

		session.removeFluidDeposit("examplemod:fluid_deposit/brine");
		session.removeFluidDeposit("pack:fluid_deposit/test");

		JsonObject tombstone = session.section("fluid_deposits")
				.getAsJsonObject("examplemod:fluid_deposit/brine");
		assertFalse(tombstone.get("enabled").getAsBoolean());
		assertTrue(tombstone.get("unassigned").getAsBoolean());
		assertFalse(session.section("fluid_deposits").has("pack:fluid_deposit/test"));
	}

	@Test
	void rangedSelectorOreIsValidWithoutAnExplicitDimension() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		JsonObject ore = session.ore("minecraft:coal_ore");
		ore.addProperty("block", "minecraft:coal_ore");
		ore.addProperty("enabled", true);
		JsonObject rule = GeologyEditorSession.defaultOreDimension();
		rule.remove("quantity");
		rule.addProperty("min_quantity", 4);
		rule.addProperty("max_quantity", 11);
		JsonArray tags = new JsonArray();
		tags.add(new JsonPrimitive("forge:stone"));
		rule.add("host_tags", tags);
		JsonObject selectors = new JsonObject();
		selectors.add("orespawn:all_except_nether_end", rule);
		ore.add("dimension_selectors", selectors);

		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());
	}

	@Test
	void standaloneFluidPickerCreatesAUsableCoveredOverworldRule() {
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		session.configureDefaultVanillaStrata();

		String id = session.assignFluidDeposit("minecraft:water");

		assertEquals("orespawn:fluid_deposit/minecraft/water", id);
		JsonObject deposit = session.fluidDeposit(id);
		assertEquals("minecraft:water", deposit.get("block").getAsString());
		JsonObject overworld = deposit.getAsJsonObject("dimensions")
				.getAsJsonObject("minecraft:overworld");
		assertEquals(2, overworld.get("min_solid_cover").getAsInt());
		assertEquals(1, overworld.get("min_solid_shell").getAsInt());
		assertEquals(4, overworld.getAsJsonArray("host_families").size());
		assertFalse(session.availableFluidBlockIds("").contains("minecraft:water"));
		assertEquals(id, session.assignFluidDeposit("minecraft:water"));
		assertEquals(1, session.section("fluid_deposits").entrySet().size());
		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());
	}

	@Test
	void namespacedGeomesCanBeAddedValidatedAndRoundTripped() {
		String geomeId = "cakeworld:cocoa_basin";
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		session.configureDefaultVanillaStrata();
		session.addGeome(geomeId);

		assertTrue(session.section("geomes").has(geomeId));
		session.weightMap("biomes", "minecraft:plains").addProperty(geomeId, 2.0D);
		session.rock("minecraft:stone").getAsJsonObject("geomes").addProperty(geomeId, 3.0D);
		java.util.List<String> errors = session.validate();
		assertTrue(errors.isEmpty(), errors.toString());

		WorldGeologyProfile saved = session.profile();
		GeologyEditorSession reopened = new GeologyEditorSession(saved);
		assertEquals(saved.rootCopy(), reopened.profile().rootCopy());
		assertTrue(reopened.validate().isEmpty(), reopened.validate().toString());
		assertEquals(2.0D, reopened.weightMap("biomes", "minecraft:plains")
				.get(geomeId).getAsDouble());
		assertEquals(3.0D, reopened.rock("minecraft:stone").getAsJsonObject("geomes")
				.get(geomeId).getAsDouble());

		GeologyEditorSession invalidSession = new GeologyEditorSession(WorldGeologyProfile.recommended(false));
		invalidSession.configureDefaultVanillaStrata();
		invalidSession.addGeome("");
		invalidSession.addGeome("BAD:UPPER");
		invalidSession.addGeome(geomeId);
		invalidSession.addGeome(geomeId);
		assertFalse(invalidSession.section("geomes").has(""));
		assertFalse(invalidSession.section("geomes").has("bad:upper"));
		assertTrue(invalidSession.section("geomes").has(geomeId));
	}

	@Test
	void oreSourceEditsStayInsideThePendingEditorSession() {
		WorldGeologyProfile original = profileWithOreSourcePolicy();
		JsonObject before = original.rootCopy();
		GeologyEditorSession session = new GeologyEditorSession(original);
		GeologyEditorSession.OreSourceGroup group = session.oreSourceGroups().get(0);
		assertEquals("keep_separate", group.mode);
		assertThrows(UnsupportedOperationException.class, () -> group.candidates.clear());
		assertThrows(UnsupportedOperationException.class,
				() -> group.outputs.put("example:other", 2.0D));

		session.setOreSourceMode(group.key, true);
		session.setOreSourceOutputMode(group.key, "custom");
		session.setOreSourcePlacement(group.key, "orespawn:standard", "mineralogy:sulfur");
		session.setOreSourceOutput(group.key, "mineralogy:sulfur", true, 3.5D);
		GeologyEditorSession.OreSourceGroup edited = session.oreSourceGroups().get(0);
		assertEquals("consolidated", edited.mode);
		assertEquals("mineralogy:sulfur", edited.placements.get("orespawn:standard"));
		assertEquals(3.5D, edited.outputs.get("mineralogy:sulfur").doubleValue());
		assertEquals(before, original.rootCopy(), "editing must not persist the profile early");
		assertFalse(before.equals(session.profile().rootCopy()));
	}

	@Test
	void equivalentPlacementRulesAreHiddenWithoutChangingThePendingProfile() {
		JsonObject root = WorldGeologyProfile.recommended(false).toJson();
		JsonObject policy = new JsonObject();
		policy.addProperty("material", "orespawn:gold");
		policy.addProperty("domain", "minecraft:overworld");
		policy.addProperty("mode", "keep_separate");
		policy.addProperty("status", "review_required");
		policy.addProperty("review_required", true);
		JsonObject outputs = new JsonObject();
		outputs.addProperty("minecraft:gold_ore", 1.0D);
		outputs.addProperty("orespawn:vanilla_gold_badlands", 1.0D);
		policy.add("outputs", outputs);
		JsonObject placements = new JsonObject();
		placements.addProperty("orespawn:standard", "minecraft:gold_ore");
		policy.add("placement_sources", placements);
		JsonArray candidates = new JsonArray();
		candidates.add(candidate("minecraft:gold_ore", "minecraft", "minecraft:gold_ore",
				"orespawn:standard", false));
		candidates.add(candidate("orespawn:vanilla_gold_badlands", "minecraft", "minecraft:gold_ore",
				"orespawn:standard", false));
		policy.add("candidates", candidates);
		JsonObject policies = new JsonObject();
		policies.add("orespawn:gold|minecraft:overworld", policy);
		root.add("ore_source_policies", policies);
		WorldGeologyProfile profile = WorldGeologyProfile.fromJson(root,
				WorldGeologyProfile.recommended(false));
		GeologyEditorSession session = new GeologyEditorSession(profile);
		JsonObject before = session.profile().rootCopy();

		assertEquals(1, session.oreSourceGroups().size());
		assertTrue(session.oreSourceGroups().get(0).isRedundantSeparatePolicy());
		assertEquals(before, session.profile().rootCopy(),
				"Listing a harmless group must not rewrite the pending profile");
	}

	@Test
	void customMaterialGroupsKeepStableIdsAndRequireConfirmedAliasMoves() {
		WorldGeologyProfile original = profileWithOreSourcePolicy();
		JsonObject before = original.rootCopy();
		GeologyEditorSession session = new GeologyEditorSession(original);

		String key = session.addOreMaterialGroup();
		String material = key.substring(0, key.indexOf('|'));
		session.renameOreMaterialGroup(material, "Volcanogenic Sulphides");
		assertTrue(session.addOreMaterialAlias(material, "oreCopperZinc", false));
		assertFalse(session.addOreMaterialAlias(material, "oreSulfur", false));
		assertTrue(session.addOreMaterialAlias(material, "oreSulfur", true));

		assertEquals(material, session.oreDictionaryOwner("oreSulfur"));
		assertEquals("Volcanogenic Sulphides", session.oreSourceGroups().stream()
				.filter(group -> material.equals(group.material)).findFirst().get().displayName);
		assertTrue(session.oreMaterialGroupsChanged());
		assertEquals(before, original.rootCopy(), "pending group edits must not persist before main Done");

		session.deleteOreMaterialGroup(material);
		assertEquals(null, session.oreDictionaryOwner("oreSulfur"));
		assertFalse(key.equals(session.addOreMaterialGroup()),
				"deleted group IDs remain reserved by their dormant policy");
		session.resetOreMaterialGroup("orespawn:sulfur");
		assertEquals("orespawn:sulfur", session.oreDictionaryOwner("oreSulfur"));
	}

	@Test
	void manageVanillaToggleRebakesNativePlacementSourcesInsideTheEditor() {
		GeologyEditorSession session = new GeologyEditorSession(profileWithNativeDiamondOre());
		GeologyEditorSession.OreSourceGroup disabled = group(session, "orespawn:diamond");
		assertFalse(session.profile().manageVanillaOres());
		assertFalse(disabled.hasManagedPlacementSource());
		assertTrue(disabled.placements.isEmpty());

		session.setManageVanillaOres(true);
		GeologyEditorSession.OreSourceGroup enabled = group(session, "orespawn:diamond");
		assertTrue(session.profile().manageVanillaOres());
		assertTrue(enabled.hasManagedPlacementSource());
		assertEquals("minecraft:diamond_ore", enabled.placements.get("orespawn:standard"));

		session.setManageVanillaOres(false);
		GeologyEditorSession.OreSourceGroup disabledAgain = group(session, "orespawn:diamond");
		assertFalse(disabledAgain.hasManagedPlacementSource());
		assertTrue(disabledAgain.placements.isEmpty());
	}

	@Test
	void vanillaAliasMoveRequiresVanillaManagementAndNeverChangesOwnershipWhenBlocked() {
		GeologyEditorSession session = new GeologyEditorSession(profileWithNativeDiamondOre());
		String customKey = session.addOreMaterialGroup();
		String customMaterial = customKey.substring(0, customKey.indexOf('|'));

		assertEquals("orespawn:diamond", session.oreDictionaryOwner("oreDiamond"));
		assertTrue(session.vanillaOreManagementRequiredForAliasMove(customMaterial, "oreDiamond"));
		assertFalse(session.addOreMaterialAlias(customMaterial, "oreDiamond", true));
		assertEquals("orespawn:diamond", session.oreDictionaryOwner("oreDiamond"));

		session.setManageVanillaOres(true);
		assertFalse(session.vanillaOreManagementRequiredForAliasMove(customMaterial, "oreDiamond"));
		assertTrue(session.addOreMaterialAlias(customMaterial, "oreDiamond", true));
		assertEquals(customMaterial, session.oreDictionaryOwner("oreDiamond"));
		assertTrue(group(session, customMaterial).hasManagedPlacementSource());
	}

	@Test
	void balancedSingleAndCustomModesKeepOneExplicitOutputPolicy() {
		GeologyEditorSession session = new GeologyEditorSession(profileWithTwoOreOutputs());
		String key = session.oreSourceGroups().get(0).key;
		GeologyEditorSession.OreSourceGroup initial = session.oreSourceGroups().get(0);
		assertTrue(initial.oreDictionaryEntries.contains("oreSulfur"));
		assertTrue(initial.oreDictionaryEntries.contains("oreSulphur"));
		assertEquals(2, initial.outputCandidates().size(),
				"the selected group exposes the union of candidates from all aliases");

		session.setOreSourceOutputMode(key, "balanced");
		GeologyEditorSession.OreSourceGroup balanced = session.oreSourceGroups().get(0);
		assertEquals(2, balanced.outputs.size());
		assertTrue(balanced.outputs.values().stream().allMatch(weight -> weight == 1.0D));
		assertFalse(balanced.needsAttention(),
				"a saved consolidated rule must no longer be marked for review");

		session.setOreSourceOutputMode(key, "single");
		GeologyEditorSession.OreSourceGroup single = session.oreSourceGroups().get(0);
		assertEquals(1, single.outputs.size());
		assertTrue(single.outputs.containsKey("mineralogy:sulfur"),
				"Single initially follows the reviewed placement priority");

		session.setOreSourceOutputMode(key, "custom");
		session.setOreSourceOutput(key, "baseminerals:sulfur", true, 2.5D);
		session.setOreSourceOutput(key, "mineralogy:sulfur", false, 1.0D);
		GeologyEditorSession.OreSourceGroup custom = session.oreSourceGroups().get(0);
		assertEquals(1, custom.outputs.size());
		assertEquals(2.5D, custom.outputs.get("baseminerals:sulfur").doubleValue());
	}

	@Test
	void outputOnlyCustomGroupCannotEnterAnInvalidBalancedPolicy() {
		JsonObject root = profileWithTwoOreOutputs().rootCopy();
		JsonObject policy = root.getAsJsonObject("ore_source_policies")
				.getAsJsonObject("orespawn:sulfur|minecraft:overworld");
		policy.getAsJsonObject("placement_sources").entrySet().clear();
		for (JsonElement element : policy.getAsJsonArray("candidates")) {
			element.getAsJsonObject().addProperty("placement_active", false);
		}
		policy.addProperty("mode", "keep_separate");
		policy.addProperty("output_mode", "custom");
		policy.addProperty("review_required", true);
		policy.addProperty("status", "review_required");
		GeologyEditorSession session = new GeologyEditorSession(WorldGeologyProfile.fromJson(
				root, WorldGeologyProfile.recommended(false)));

		session.setOreSourceOutputMode("orespawn:sulfur|minecraft:overworld", "balanced");

		GeologyEditorSession.OreSourceGroup group = session.oreSourceGroups().get(0);
		assertEquals("keep_separate", group.mode);
		assertEquals("custom", group.outputMode);
		assertTrue(group.needsReview(), "Keep Original remains available for explicit review");
		assertFalse(group.hasManagedPlacementSource());
	}

	@Test
	void keepOriginalRestoresThePolicySnapshotFromWhenTheEditorOpened() {
		GeologyEditorSession session = new GeologyEditorSession(profileWithTwoOreOutputs());
		GeologyEditorSession.OreSourceGroup original = session.oreSourceGroups().get(0);
		String key = original.key;

		session.setOreSourceOutputMode(key, "custom");
		session.setOreSourceOutput(key, "mineralogy:sulfur", false, 1.0D);
		session.setOreSourceOutput(key, "baseminerals:sulfur", true, 4.5D);
		session.setOreSourcePlacement(key, "orespawn:standard", "baseminerals:sulfur");
		GeologyEditorSession.OreSourceGroup edited = session.oreSourceGroups().get(0);
		assertEquals(java.util.Collections.singleton("baseminerals:sulfur"), edited.outputs.keySet());
		assertEquals("baseminerals:sulfur", edited.placements.get("orespawn:standard"));

		session.restoreOreSourceOriginalMode(key);
		GeologyEditorSession.OreSourceGroup restored = session.oreSourceGroups().get(0);
		assertEquals("keep_separate", restored.mode);
		assertEquals(original.outputMode, restored.outputMode);
		assertEquals(original.outputs, restored.outputs);
		assertEquals(original.placements, restored.placements);
	}

	@Test
	void acceptingKeepOriginalClearsReviewWithoutChangingItsPolicy() {
		WorldGeologyProfile originalProfile = profileWithTwoOreOutputs();
		GeologyEditorSession session = new GeologyEditorSession(originalProfile);
		GeologyEditorSession.OreSourceGroup before = session.oreSourceGroups().get(0);
		JsonObject persistedBefore = originalProfile.rootCopy();

		assertTrue(before.needsReview());
		assertEquals("keep_separate", before.mode);
		session.acceptOreSourcePolicy(before.key);

		GeologyEditorSession.OreSourceGroup accepted = session.oreSourceGroups().get(0);
		assertFalse(accepted.needsReview());
		assertFalse(accepted.needsAttention());
		assertEquals("separate", accepted.status);
		assertEquals(before.mode, accepted.mode);
		assertEquals(before.outputMode, accepted.outputMode);
		assertEquals(before.outputs, accepted.outputs);
		assertEquals(before.placements, accepted.placements);
		assertEquals(persistedBefore, originalProfile.rootCopy(),
				"Accept remains pending until the main editor saves the profile");
	}

	@Test
	void missingExternalDuplicateCannotOverrideItsLoadedManagedOutput() {
		JsonObject root = profileWithTwoOreOutputs().rootCopy();
		JsonObject policy = root.getAsJsonObject("ore_source_policies")
				.getAsJsonObject("orespawn:sulfur|minecraft:overworld");
		JsonObject managed = policy.getAsJsonArray("candidates").get(1).getAsJsonObject();
		JsonObject external = zone.moddev.mc.orespawn.util.JsonCopies.copy(managed);
		external.addProperty("source_id", "external/minecraft:gold_ore/0");
		external.addProperty("loaded", false);
		external.addProperty("placement_active", false);
		external.addProperty("external", true);
		policy.getAsJsonArray("candidates").add(external);
		policy.addProperty("review_required", false);
		policy.addProperty("status", "external_generation");
		WorldGeologyProfile profile = WorldGeologyProfile.fromJson(root,
				WorldGeologyProfile.recommended(false));
		JsonObject before = profile.rootCopy();

		GeologyEditorSession.OreSourceGroup group =
				new GeologyEditorSession(profile).oreSourceGroups().get(0);

		assertEquals("separate", group.status);
		assertFalse(group.needsAttention());
		assertEquals(before, profile.rootCopy(), "Deriving the visible status must not rewrite the profile");
	}

	private static WorldGeologyProfile profileWithOreSourcePolicy() {
		JsonObject root = WorldGeologyProfile.recommended(false).toJson();
		JsonObject policy = new JsonObject();
		policy.addProperty("material", "orespawn:sulfur");
		policy.addProperty("domain", "minecraft:overworld");
		policy.addProperty("mode", "keep_separate");
		policy.addProperty("status", "review_required");
		policy.addProperty("review_required", true);
		JsonObject outputs = new JsonObject();
		outputs.addProperty("mineralogy:sulfur", 1.0D);
		policy.add("outputs", outputs);
		policy.add("placement_sources", new JsonObject());
		JsonObject candidate = new JsonObject();
		candidate.addProperty("source_id", "mineralogy:sulfur");
		candidate.addProperty("owner", "mineralogy");
		candidate.addProperty("owner_name", "Mineralogy");
		candidate.addProperty("owner_version", "6.0.0");
		candidate.addProperty("registry_id", "minecraft:iron_ore");
		candidate.addProperty("metadata", 0);
		candidate.addProperty("placement_channel", "orespawn:standard");
		candidate.addProperty("loaded", true);
		candidate.addProperty("placement_active", true);
		candidate.addProperty("external", false);
		candidate.addProperty("enrichment", false);
		candidate.addProperty("review_required", true);
		candidate.addProperty("material_declared", false);
		candidate.add("ore_dictionary", new JsonArray());
		JsonArray candidates = new JsonArray();
		candidates.add(candidate);
		policy.add("candidates", candidates);
		JsonObject policies = new JsonObject();
		policies.add("orespawn:sulfur|minecraft:overworld", policy);
		root.add("ore_source_policies", policies);
		return WorldGeologyProfile.fromJson(root, WorldGeologyProfile.recommended(false));
	}

	private static WorldGeologyProfile profileWithTwoOreOutputs() {
		JsonObject root = profileWithOreSourcePolicy().rootCopy();
		JsonObject policy = root.getAsJsonObject("ore_source_policies")
				.getAsJsonObject("orespawn:sulfur|minecraft:overworld");
		policy.getAsJsonObject("outputs").addProperty("baseminerals:sulfur", 1.0D);
		policy.getAsJsonObject("placement_sources")
				.addProperty("orespawn:standard", "mineralogy:sulfur");
		policy.getAsJsonArray("candidates").add(candidate("baseminerals:sulfur",
				"baseminerals", "minecraft:gold_ore", "orespawn:standard", false));
		return WorldGeologyProfile.fromJson(root, WorldGeologyProfile.recommended(false));
	}

	private static WorldGeologyProfile profileWithNativeDiamondOre() {
		WorldGeologyProfile recommended = WorldGeologyProfile.recommended(false);
		JsonObject root = recommended.rootCopy();
		root.addProperty("manage_vanilla_ores", false);
		JsonObject ore = new JsonObject();
		ore.addProperty("enabled", true);
		ore.addProperty("source_mod", "minecraft");
		ore.addProperty("native_generation", true);
		ore.addProperty("block", "minecraft:diamond_ore");
		JsonObject rule = new JsonObject();
		rule.addProperty("enabled", true);
		rule.addProperty("pattern", "vein");
		JsonObject dimensions = new JsonObject();
		dimensions.add("minecraft:overworld", rule);
		ore.add("dimensions", dimensions);
		JsonObject ores = new JsonObject();
		ores.add("minecraft:diamond_ore", ore);
		root.add("ores", ores);
		root.add("ore_source_policies", new JsonObject());
		return WorldGeologyProfile.fromGlobalConfig(root,
				recommended.geologyMode(), recommended.placeFluidDeposits());
	}

	private static GeologyEditorSession.OreSourceGroup group(
			GeologyEditorSession session, String material) {
		return session.oreSourceGroups().stream()
				.filter(group -> material.equals(group.material))
				.findFirst().orElseThrow(() -> new AssertionError("Missing group " + material));
	}

	private static JsonObject candidate(String sourceId, String owner, String registryId,
			String channel, boolean reviewRequired) {
		JsonObject candidate = new JsonObject();
		candidate.addProperty("source_id", sourceId);
		candidate.addProperty("owner", owner);
		candidate.addProperty("owner_name", owner);
		candidate.addProperty("owner_version", "");
		candidate.addProperty("registry_id", registryId);
		candidate.addProperty("metadata", 0);
		candidate.addProperty("placement_channel", channel);
		candidate.addProperty("loaded", true);
		candidate.addProperty("placement_active", true);
		candidate.addProperty("external", false);
		candidate.addProperty("enrichment", false);
		candidate.addProperty("review_required", reviewRequired);
		candidate.addProperty("material_declared", false);
		candidate.add("ore_dictionary", new JsonArray());
		return candidate;
	}
}
