package zone.moddev.mc.orespawn.init;

import com.mojang.serialization.MapCodec;

import zone.moddev.mc.orespawn.OreSpawn;
import zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration;
import zone.moddev.mc.orespawn.worldgen.FlatBedrockFeature;
import zone.moddev.mc.orespawn.worldgen.FluidDepositFeature;
import zone.moddev.mc.orespawn.worldgen.StoneReplacer;
import zone.moddev.mc.orespawn.worldgen.BiomeSurfaceFeature;
import zone.moddev.mc.orespawn.worldgen.VanillaOreFeatureGate;
import zone.moddev.mc.orespawn.worldgen.VanillaSpringCompatibility;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class Features {
	private static final DeferredRegister<MapCodec<? extends Feature>> FEATURES =
			DeferredRegister.create(BuiltInRegistries.FEATURE_TYPE, OreSpawn.MODID);

	static {
		FEATURES.register("stone_replacer", () -> StoneReplacer.CODEC);
		FEATURES.register("matching_stone_gate", StoneReplacer::matchingStoneGateCodec);
		FEATURES.register("managed_ores", () -> OreSpawnOreGeneration.CODEC);
		FEATURES.register("fluid_deposits", () -> FluidDepositFeature.CODEC);
		FEATURES.register("flat_bedrock", () -> FlatBedrockFeature.CODEC);
		FEATURES.register("biome_surfaces", () -> BiomeSurfaceFeature.CODEC);
		FEATURES.register("spring_host", VanillaSpringCompatibility::codec);
		VanillaOreFeatureGate.registerFeatures(FEATURES);
	}

	public static void register(IEventBus bus) {
		FEATURES.register(bus);
	}

	private Features() {
		throw new IllegalAccessError("Not an instantiable class");
	}
}
