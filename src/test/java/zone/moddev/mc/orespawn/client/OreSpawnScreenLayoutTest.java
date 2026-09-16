package zone.moddev.mc.orespawn.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class OreSpawnScreenLayoutTest {
	@Test
	void everyConcreteScreenClearsThePreviousFrameBeforeDrawingWidgets() throws Exception {
		Path directory = Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client");
		List<Path> screens;
		try (Stream<Path> files = Files.list(directory)) {
			screens = files
					.filter(path -> path.getFileName().toString().endsWith("Screen.java"))
					.filter(path -> !path.getFileName().toString().equals("OreSpawnScreen.java"))
					.sorted()
					.collect(Collectors.toList());
		}
		assertEquals(26, screens.size(), "Review this render-order gate when screens are added or removed");
		for (Path screen : screens) {
			String source = new String(Files.readAllBytes(screen), StandardCharsets.UTF_8);
			int render = source.indexOf(
					"public void render(int mouseX, int mouseY, float partialTick)");
			int background = source.indexOf("renderBackground();", render);
			int widgets = source.indexOf("super.render(mouseX, mouseY, partialTick);", render);
			String name = screen.getFileName().toString();
			assertTrue(render >= 0, name + " must own its 1.10.2 render pass");
			assertTrue(background > render, name + " must clear the previous frame");
			assertTrue(widgets > background, name + " must clear before drawing widgets and tooltips");
		}
	}

	@Test
	void compactMainRowsStayAboveFooter() {
		assertRowsClearFooter(240);
	}

	@Test
	void normalMainRowsStayAboveFooter() {
		assertRowsClearFooter(270);
	}

	@Test
	void compactOrePlacementRowsStayAboveFooterAtGuiScaleThree() {
		assertCompactOrePlacementClearsFooter(256);
	}

	@Test
	void compactOrePlacementRowsStayAboveFooterAtMinimumTestHeight() {
		assertCompactOrePlacementClearsFooter(240);
	}

	@Test
	void advancedAndFluidEditorsShareTheRecoveredTerrainRow() throws Exception {
		Path screen = Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client", "OreSpawnWorldSettingsScreen.java");
		String source = new String(Files.readAllBytes(screen), StandardCharsets.UTF_8);
		int advanced = source.indexOf("new TextComponentTranslation(\"button.orespawn.advanced\")");
		int advancedCall = source.lastIndexOf("addButton", advanced);
		int fluid = source.indexOf("fluidEditorLabel()", advanced);
		int fluidCall = source.lastIndexOf("addButton", fluid);
		assertTrue(advanced > 0 && fluid > advanced);
		assertTrue(source.substring(advancedCall, advanced)
				.contains("font, left, top + (row * rowIndex)"));
		assertTrue(source.substring(fluidCall, fluid)
				.contains("font, right, top + (row * rowIndex++)"));
	}

	@Test
	void oreSourcesAndAddBlockShareTheCompactOreFooter() throws Exception {
		String source = screenSource("GeologyMaterialsScreen.java");
		int width = source.indexOf("int addWidth = tab == MaterialTab.ORES");
		int sources = source.indexOf("button.orespawn.ore_sources", width);
		int add = source.indexOf("button.orespawn.add_block", sources);
		assertTrue(width >= 0 && sources > width && add > sources);
		assertTrue(source.substring(width, add).contains("addX - addWidth - 5"));
		assertTrue(source.substring(width, add).contains("addX, controlsY, addWidth, 20"));
	}

	@Test
	void oreSourceDirectoryPaginatesClipsAndPreservesParentNavigation() throws Exception {
		assertEquals(1, OreSourceListScreen.pageCount(0, 4));
		assertEquals(1, OreSourceListScreen.pageCount(4, 4));
		assertEquals(2, OreSourceListScreen.pageCount(5, 4));
		String list = screenSource("OreSourceListScreen.java");
		assertTrue(list.contains("private static final int WIDE_MINIMUM = 520"));
		assertTrue(list.contains("compact(width)"));
		assertTrue(list.contains("initDirectory("));
		assertTrue(list.contains("initOutputs("));
		assertTrue(list.contains("OreSpawnScreenLayout.fit"));
		assertTrue(list.contains("button.orespawn.ore_source.needs_attention"));
		assertTrue(list.contains("button.orespawn.ore_source.all_groups"));
		assertTrue(list.contains("button.orespawn.ore_source.add_group"));
		assertTrue(list.contains("outputCandidates()"));
		assertTrue(list.contains("button.orespawn.ore_source.advanced"));
		assertTrue(list.contains(", 20,"), "Material Groups must use normal Forge-height controls");
		assertTrue(list.contains("minecraft.displayGuiScreen(parent)"));
		assertTrue(list.contains("toggle.enabled = candidate.loaded && !candidate.enrichment"));
		assertFalse(Files.exists(Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client", "OreSourceDetailScreen.java")),
				"The technical detail screen must not remain as a second competing flow");
	}

	@Test
	void materialGroupDefaultsPersistOnlyThroughMainDone() throws Exception {
		String main = screenSource("OreSpawnWorldSettingsScreen.java");
		String groups = screenSource("OreSourceListScreen.java");
		int save = main.indexOf("private void saveAndClose()");
		int persistence = main.indexOf("GeomeConfig.persistOreMaterialGroups", save);
		assertTrue(save >= 0 && persistence > save);
		assertFalse(groups.contains("persistOreMaterialGroups"),
				"leaving Material Groups must not persist future-world defaults");
		assertTrue(groups.contains("minecraft.displayGuiScreen(parent)"),
				"Done and Escape return to the pending ORES editor session");
	}

	private static String screenSource(String name) throws Exception {
		Path screen = Paths.get("src", "main", "java", "zone", "moddev", "mc",
				"orespawn", "client", name);
		return new String(Files.readAllBytes(screen), StandardCharsets.UTF_8);
	}

	private static void assertRowsClearFooter(int height) {
		int rows = 8;
		int top = OreSpawnScreenLayout.mainTop(height);
		int available = OreSpawnScreenLayout.footerY(height) - top - 20 - 4;
		int spacing = Math.min(OreSpawnScreenLayout.mainRowSpacing(height),
				Math.max(20, available / (rows - 1)));
		int lastRowBottom = OreSpawnScreenLayout.mainTop(height)
				+ (spacing * (rows - 1)) + 20;
		assertTrue(lastRowBottom < OreSpawnScreenLayout.footerY(height));
	}

	private static void assertCompactOrePlacementClearsFooter(int height) {
		int lastFieldBottom = OreSpawnScreenLayout.compactOrePlacementFieldY(height, 2) + 20;
		assertTrue(lastFieldBottom < OreSpawnScreenLayout.footerY(height));
	}
}
