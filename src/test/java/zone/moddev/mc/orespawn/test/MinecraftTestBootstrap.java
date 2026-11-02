package zone.moddev.mc.orespawn.test;

import net.minecraft.server.Bootstrap;
import net.minecraft.SharedConstants;

/** Initializes vanilla registries for tests that inspect biome definitions. */
public final class MinecraftTestBootstrap {
	private MinecraftTestBootstrap() { }

	public static void registerVanilla() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	/** Unit tests have no mod-loading bus to register OreSpawn's pattern factories. */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static void registerOrePatterns() throws ReflectiveOperationException {
		registerVanilla();
		var registry = zone.moddev.mc.orespawn.init.OreSpawnPatterns.registry();
		if (registry.size() != 0) return;
		var types = zone.moddev.mc.orespawn.init.OreSpawnPatterns.class.getDeclaredField("TYPES");
		types.setAccessible(true);
		var entries = net.neoforged.neoforge.registries.DeferredRegister.class.getDeclaredField("entries");
		entries.setAccessible(true);
		java.util.Map<?, ?> factories = (java.util.Map<?, ?>) entries.get(types.get(null));
		for (var entry : factories.entrySet()) {
			var holder = (net.neoforged.neoforge.registries.DeferredHolder<?, ?>) entry.getKey();
			var factory = (java.util.function.Supplier<?>) entry.getValue();
			net.minecraft.core.Registry.register((net.minecraft.core.Registry) registry, holder.getId(), factory.get());
		}
	}
}
