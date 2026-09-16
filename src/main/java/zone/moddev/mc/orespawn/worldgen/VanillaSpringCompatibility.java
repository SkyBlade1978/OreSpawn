package zone.moddev.mc.orespawn.worldgen;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.SpringFeature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Extends vanilla spring hosts without mutating Minecraft's immutable 26.3
 * feature records. Spring features are wrapped at their original placement
 * position and read the current baked geology hosts when invoked.
 */
public final class VanillaSpringCompatibility {
	private static volatile HolderSet<Block> additionalHosts = HolderSet.direct();
	private static final Map<PlacedFeature, Holder<PlacedFeature>> WRAPPED_FEATURES =
			new IdentityHashMap<>();

	private VanillaSpringCompatibility() {
		throw new IllegalAccessError("Not an instantiable class");
	}

	public static MapCodec<? extends Feature> codec() {
		return SpringHostFeature.CODEC;
	}

	static synchronized void refresh(RegistryAccess registries, BakedGeomeConfig config) {
		Set<Block> rocks = Collections.newSetFromMap(new IdentityHashMap<>());
		if (config != null) config.addRockBlocks(rocks);
		additionalHosts = HolderSet.direct(Block::builtInRegistryHolder, rocks.toArray(new Block[0]));
	}

	static synchronized void clear(RegistryAccess registries) {
		additionalHosts = HolderSet.direct();
		WRAPPED_FEATURES.clear();
	}

	static synchronized boolean wrapFeatureList(List<Holder<PlacedFeature>> features) {
		boolean changed = false;
		for (int index = 0; index < features.size(); index++) {
			Holder<PlacedFeature> placedHolder = features.get(index);
			PlacedFeature placed = placedHolder.value();
			Feature feature = placed.feature().value();
			if (feature instanceof SpringFeature spring) {
				// Minecraft 26.3 indexes placed features by reference identity when it
				// assigns decoration seeds. Vanilla shares one registered spring value
				// across biomes, so its replacement must be shared in exactly the same
				// way or vanilla spring coordinates move with the same world seed.
				Holder<PlacedFeature> wrapped = WRAPPED_FEATURES.computeIfAbsent(placed,
						ignored -> Holder.direct(new PlacedFeature(
								Holder.direct(new SpringHostFeature(spring)), placed.placement())));
				features.set(index, wrapped);
				changed = true;
			}
		}
		return changed;
	}

	static HolderSet<Block> merge(HolderSet<Block> original, Iterable<Block> additionalBlocks) {
		List<Holder<Block>> holders = new ArrayList<>(original.size());
		Set<Block> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		boolean changed = false;
		for (Holder<Block> holder : original) {
			if (seen.add(holder.value())) holders.add(holder);
		}
		for (Block block : additionalBlocks) {
			if (seen.add(block)) {
				holders.add(block.builtInRegistryHolder());
				changed = true;
			}
		}
		return changed ? HolderSet.direct(holders) : original;
	}

	static SpringFeature withAdditionalHosts(SpringFeature original,
			Iterable<Block> additionalBlocks) {
		HolderSet<Block> hosts = merge(original.validBlocks(), additionalBlocks);
		return withHosts(original, hosts);
	}

	private static SpringFeature withAdditionalHosts(SpringFeature original,
			HolderSet<Block> additionalBlocks) {
		List<Holder<Block>> holders = new ArrayList<>();
		Set<Block> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (Holder<Block> holder : original.validBlocks()) {
			if (seen.add(holder.value())) holders.add(holder);
		}
		for (Holder<Block> holder : additionalBlocks) {
			if (seen.add(holder.value())) holders.add(holder);
		}
		HolderSet<Block> hosts = holders.size() == original.validBlocks().size()
				? original.validBlocks() : HolderSet.direct(holders);
		return withHosts(original, hosts);
	}

	private static SpringFeature withHosts(SpringFeature original, HolderSet<Block> hosts) {
		return hosts == original.validBlocks() ? original : new SpringFeature(
				original.state(), original.requiresBlockBelow(), original.rockCount(),
				original.holeCount(), hosts);
	}

	static final class SpringHostFeature implements Feature {
		static final MapCodec<SpringHostFeature> CODEC = SpringFeature.CODEC
				.fieldOf("delegate")
				.xmap(SpringHostFeature::new, value -> value.delegate);
		private final SpringFeature delegate;
		private volatile HolderSet<Block> cachedAdditions;
		private volatile SpringFeature cachedDelegate;

		SpringHostFeature(SpringFeature delegate) {
			this.delegate = delegate;
		}

		@Override
		public MapCodec<SpringHostFeature> codec() {
			return CODEC;
		}

		@Override
		public boolean place(WorldGenLevel level, ChunkGenerator chunkGenerator,
				RandomSource random, BlockPos origin) {
			HolderSet<Block> additions = additionalHosts;
			SpringFeature current = cachedDelegate;
			if (cachedAdditions != additions || current == null) {
				synchronized (this) {
					if (cachedAdditions != additions || cachedDelegate == null) {
						cachedDelegate = additions.size() == 0 ? delegate
								: withAdditionalHosts(delegate, additions);
						cachedAdditions = additions;
					}
					current = cachedDelegate;
				}
			}
			return current.place(level, chunkGenerator, random, origin);
		}
	}
}
