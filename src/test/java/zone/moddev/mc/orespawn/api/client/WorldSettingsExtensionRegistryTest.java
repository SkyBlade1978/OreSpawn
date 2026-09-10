package zone.moddev.mc.orespawn.api.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

class WorldSettingsExtensionRegistryTest {
	@Test
	void preservesRegistrationOrderAndReturnsAnImmutableSnapshot() {
		WorldSettingsExtension first = register("ordered_first", parent -> new GuiScreen() { });
		WorldSettingsExtension second = register("ordered_second", parent -> new GuiScreen() { });
		List<WorldSettingsExtension> extensions = WorldSettingsExtensionRegistry.extensions();

		assertTrue(extensions.indexOf(first) < extensions.indexOf(second));
		assertThrows(UnsupportedOperationException.class, () -> extensions.add(first));
	}

	@Test
	void rejectsDuplicateIdsAndBlankTranslationKeys() {
		ResourceLocation id = id("duplicate");
		WorldSettingsExtensionRegistry.register(id, "button.realisticdeposits.settings",
				parent -> new GuiScreen() { });
		assertThrows(IllegalStateException.class, () -> WorldSettingsExtensionRegistry.register(id,
				"button.realisticdeposits.other", parent -> new GuiScreen() { }));
		assertThrows(IllegalArgumentException.class, () -> WorldSettingsExtensionRegistry.register(
				id("blank"), "  ", parent -> new GuiScreen() { }));
	}

	@Test
	void passesTheCurrentOreSpawnScreenToTheFactory() {
		AtomicReference<GuiScreen> receivedParent = new AtomicReference<>();
		GuiScreen expectedParent = new GuiScreen() { };
		GuiScreen expectedChild = new GuiScreen() { };
		WorldSettingsExtension extension = register("parent", parent -> {
			receivedParent.set(parent);
			return expectedChild;
		});

		assertSame(expectedChild, extension.createScreen(expectedParent));
		assertSame(expectedParent, receivedParent.get());
		assertEquals("button.realisticdeposits.settings", extension.buttonTranslationKey());
	}

	@Test
	void worldSettingsScreenOwnsExtensionButtonPlacementAndNavigation() throws Exception {
		String source = new String(Files.readAllBytes(Paths.get("src", "main", "java", "zone",
				"moddev", "mc", "orespawn", "client", "OreSpawnWorldSettingsScreen.java")),
				StandardCharsets.UTF_8);
		assertTrue(source.contains("WorldSettingsExtensionRegistry.extensions()"));
		assertTrue(source.contains("new TextComponentTranslation(extension.buttonTranslationKey())"));
		assertTrue(source.contains("extension.createScreen(this)"));
	}

	private static WorldSettingsExtension register(String path, WorldSettingsScreenFactory factory) {
		return WorldSettingsExtensionRegistry.register(id(path),
				"button.realisticdeposits.settings", factory);
	}

	private static ResourceLocation id(String path) {
		return new ResourceLocation("realisticdeposits", path);
	}
}
