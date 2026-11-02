package zone.moddev.mc.orespawn.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.world.level.LevelReader;

import org.junit.jupiter.api.Test;

class TerrainBiomeLookupTest {
	@Test
	void nativeUnloadedBiomeLookupNeverRequestsChunkGeneration() {
		AtomicInteger probes = new AtomicInteger();
		LevelReader source = (LevelReader) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { LevelReader.class }, (proxy, method, args) -> {
			if (method.getName().equals("getChunk")) {
				assertEquals(false, args[3], "read-only sampling must not create a missing chunk");
				probes.incrementAndGet();
				return null;
			}
			if (method.getName().equals("getUncachedNoiseBiome")) return null;
			if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
			throw new AssertionError("Unexpected world read: " + method);
		});
		for (int x : new int[] { -1_000_001, -17, 0, 16, 1_000_001 }) {
			assertNull(TerrainBiomeLookup.atBlock(source, x, -96, -x));
		}
		assertEquals(5, probes.get());
	}

	@Test
	void geologyAndSamplerHeightsResolveThroughTheSameQuartBiome() {
		AtomicReference<String> coordinates = new AtomicReference<>();
		assertNull(TerrainBiomeLookup.atBlock((x, y, z) -> {
			coordinates.set(x + "," + y + "," + z);
			return null;
		}, 13, 62, -32));
		assertEquals("3,15,-8", coordinates.get());

		assertNull(TerrainBiomeLookup.atBlock((x, y, z) -> {
			coordinates.set(x + "," + y + "," + z);
			return null;
		}, 13, 63, -32));
		assertEquals("3,15,-8", coordinates.get(),
				"later surface work must not move an adjacent height into a fuzzy biome cell");
	}
}
