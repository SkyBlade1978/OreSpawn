package zone.moddev.mc.orespawn.worldgen;

import java.util.Collections;

import net.minecraft.core.Holder;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Creates runtime-only holders for profile-driven features. */
final class WorldgenFeatureHolders {
	private WorldgenFeatureHolders() {
	}

	static Holder<PlacedFeature> direct(Feature feature) {
		return Holder.direct(new PlacedFeature(Holder.direct(feature), Collections.emptyList()));
	}
}
