package zone.moddev.mc.orespawn.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.stream.Stream;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.data.worldgen.BootstrapContext;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import org.junit.jupiter.api.Test;

class OreSpawnBiomesTest {
	@Test
	void copiesNew263BiomeThroughDynamicRegistryBootstrap() {
		HolderLookup.Provider vanilla = VanillaRegistries.createWorldLookup();
		MappedRegistry<Biome> generated = new MappedRegistry<>(Registries.BIOME,
				Lifecycle.stable());
		BootstrapContext<Biome> context = new BootstrapContext<>() {
			@Override
			public Holder.Reference<Biome> register(ResourceKey<Biome> key, Biome value) {
				return generated.register(key, value,
						net.minecraft.core.RegistrationInfo.BUILT_IN);
			}

			@Override
			public <S> HolderGetter<S> lookup(
					ResourceKey<? extends Registry<? extends S>> key) {
				return vanilla.lookupOrThrow(key);
			}

			@Override
			public <S> Stream<Holder.Reference<S>> listContextElements(
					ResourceKey<? extends Registry<? extends S>> key) {
				return vanilla.lookupOrThrow(key).listElements();
			}
		};
		ResourceKey<Biome> target = ResourceKey.create(Registries.BIOME,
				Identifier.parse("test:candy_plains"));
		ResourceKey<Biome> dappledForest = ResourceKey.create(Registries.BIOME,
				Identifier.parse("minecraft:dappled_forest"));
		Biome source = vanilla.getOrThrow(dappledForest).value();

		Holder.Reference<Biome> registered = OreSpawnBiomes.copyAndRegister(
				context, target, dappledForest,
				builder -> builder.temperature(1.35F).downfall(0.15F));

		Biome copy = registered.value();
		assertSame(copy, generated.get(target).orElseThrow().value());
		assertEquals(1.35F, copy.getModifiedClimateSettings().temperature());
		assertEquals(0.15F, copy.getModifiedClimateSettings().downfall());
		assertEquals(source.getAttributes(), copy.getAttributes());
		assertEquals(source.getModifiedSpecialEffects(), copy.getModifiedSpecialEffects());
		assertSame(source.getGenerationSettings(), copy.getGenerationSettings());
	}
}
