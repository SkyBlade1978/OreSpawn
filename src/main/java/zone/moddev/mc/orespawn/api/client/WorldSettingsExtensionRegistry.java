package zone.moddev.mc.orespawn.api.client;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

/** Client-only registry for add-on actions in OreSpawn's world-settings screen. */
@SideOnly(Side.CLIENT)
public final class WorldSettingsExtensionRegistry {
	private static final Map<ResourceLocation, WorldSettingsExtension> EXTENSIONS =
			new LinkedHashMap<>();

	private WorldSettingsExtensionRegistry() {
	}

	/**
	 * Registers one translated world-settings action in deterministic registration
	 * order. Registration must occur during client initialization.
	 *
	 * @throws IllegalStateException if the identifier is already registered
	 */
	public static synchronized WorldSettingsExtension register(ResourceLocation id,
			String buttonTranslationKey, WorldSettingsScreenFactory screenFactory) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(screenFactory, "screenFactory");
		if (buttonTranslationKey == null || buttonTranslationKey.trim().isEmpty()) {
			throw new IllegalArgumentException("buttonTranslationKey cannot be blank");
		}
		if (EXTENSIONS.containsKey(id)) {
			throw new IllegalStateException("Duplicate OreSpawn world-settings extension: " + id);
		}
		WorldSettingsExtension extension = new WorldSettingsExtension(id,
				buttonTranslationKey, screenFactory);
		EXTENSIONS.put(id, extension);
		return extension;
	}

	/** Returns an immutable snapshot in deterministic registration order. */
	public static synchronized List<WorldSettingsExtension> extensions() {
		return Collections.unmodifiableList(new ArrayList<>(EXTENSIONS.values()));
	}
}
