package zone.moddev.mc.orespawn;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.neoforge.resource.ResourcePackLoader;
import org.junit.jupiter.api.Test;

class PackMetadataTest {
	@Test
	void modAndFixturePacksAreCompatibleAsResourcesAndData() throws Exception {
		try (var paths = Files.walk(Path.of("src"))) {
			for (Path path : paths.filter(file -> file.getFileName().toString().equals("pack.mcmeta")).toList()) {
				for (PackType type : PackType.values()) {
					PackLocationInfo location = new PackLocationInfo("orespawn-test",
							Component.literal("OreSpawn"), PackSource.DEFAULT, Optional.empty());
					Pack pack = ResourcePackLoader.readWithOptionalMeta(location,
							new PathPackResources.PathResourcesSupplier(path.getParent()), type,
							new PackSelectionConfig(false, Pack.Position.TOP, false));
					assertEquals(PackCompatibility.COMPATIBLE, pack.getCompatibility(), path + " as " + type);
				}
			}
		}
	}
}
