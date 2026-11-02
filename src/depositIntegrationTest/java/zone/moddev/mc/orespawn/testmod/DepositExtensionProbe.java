package zone.moddev.mc.orespawn.testmod;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import zone.moddev.mc.orespawn.api.OreSpawnApi;
import zone.moddev.mc.orespawn.worldgen.GeomeConfig;
import zone.moddev.mc.orespawn.worldgen.OreSpawnOreGeneration;
import zone.moddev.mc.orespawn.worldgen.VanillaOreFeatureGate;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfile;
import zone.moddev.mc.orespawn.worldgen.WorldGeologyProfileManager;

/** Runs against the installed jar, not the development classpath. */
@Mod("depositprobe")
public final class DepositExtensionProbe {
    private final String phase = System.getProperty("depositprobe.phase", "fresh");
    private boolean complete;

    public DepositExtensionProbe() {
        try {
            JsonObject provider = new JsonObject();
            provider.addProperty("schema_version", 5);
            provider.addProperty("provider_modid", "depositprobe");
            provider.addProperty("provider_revision", "fresh".equals(phase) ? 1 : 2);
            provider.addProperty("merge_new_entries_into_existing_worlds", "optin".equals(phase));
            JsonObject rocks = new JsonObject();
            rocks.add("depositprobe:slate", rock("minecraft:calcite"));
            if (!"fresh".equals(phase)) rocks.add("depositprobe:new_rock", rock("minecraft:basalt"));
            provider.add("rocks", rocks);
            Files.createDirectories(Path.of("config"));
            write(Path.of("config/depositprobe-orespawn.json"), provider);
        } catch (Exception failure) {
            throw new IllegalStateException("Could not prepare disposable provider", failure);
        }
        NeoForge.EVENT_BUS.addListener(this::audit);
        NeoForge.EVENT_BUS.addListener(this::stop);
    }

    private static JsonObject rock(String block) {
        JsonObject rock = new JsonObject();
        rock.addProperty("block", block);
        rock.addProperty("family", "metamorphic");
        rock.addProperty("weight", 1);
        rock.addProperty("enabled", true);
        rock.addProperty("ore_replaceable", true);
        return rock;
    }

    private void stop(ServerTickEvent.Post event) {
        if (complete && event.getServer().getTickCount() >= 20) event.getServer().halt(false);
    }

    private void audit(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        WorldGeologyProfile saved = WorldGeologyProfileManager.activeProfile();
        Path world = server.getWorldPath(LevelResource.ROOT);
        Path profile = world.resolve("serverconfig/orespawn-worldgen.json");
        try {
            JsonObject root = saved.rootCopy();
            JsonObject rocks = root.getAsJsonObject("rocks");
            require(rocks.has("depositprobe:slate"), "Fresh provider rock is missing");
            require(rocks.has("depositprobe:new_rock") == "optin".equals(phase),
                    "Provider merge preference was ignored");
            if (!"fresh".equals(phase)) {
                require(rocks.getAsJsonObject("depositprobe:slate").get("weight").getAsDouble() == 0.25,
                        "Provider update overwrote the saved edit");
                require(Files.readString(world.resolve("deposit-chunk.sha256")).equals(hash(level.getChunk(-40, 40))),
                        "Reload changed an existing chunk");
            } else {
                JsonObject sampling = saved.rootCopy();
                JsonObject dimensions = new JsonObject();
                JsonObject terrain = new JsonObject();
                terrain.addProperty("enabled", true);
                JsonArray hosts = new JsonArray();
                hosts.add("minecraft:stone");
                terrain.add("host_blocks", hosts);
                dimensions.add("minecraft:overworld", terrain);
                sampling.add("terrain_dimensions", dimensions);
                activate(WorldGeologyProfile.fromJson(sampling, saved));
                int before = level.getChunkSource().getLoadedChunksCount();
                var sampler = OreSpawnApi.createSampler(level).orElseThrow();
                for (int coordinate : new int[] { -1000001, -17, 1000001 }) {
                    sampler.sampleColumn(coordinate, 80, -coordinate).rockAt(-100);
                }
                require(level.getChunkSource().getLoadedChunksCount() == before, "Sampling loaded missing chunks");
                System.out.println("DEPOSIT_EXTENSION sampler_loaded_chunks=" + before + " unchanged=true");
                for (double scale : new double[] { 0, 0.25, 1 }) exercise(level, scale);
                activate(saved);
                Files.writeString(world.resolve("deposit-chunk.sha256"), hash(level.getChunk(-40, 40)));
                JsonObject edited = JsonParser.parseString(Files.readString(profile)).getAsJsonObject();
                edited.getAsJsonObject("rocks").getAsJsonObject("depositprobe:slate").addProperty("weight", 0.25);
                write(profile, edited);
            }
            complete = true;
            Files.writeString(world.resolve("deposit-extension-" + phase + ".txt"), "verified " + phase);
            System.out.println("DEPOSIT_EXTENSION_PASS " + phase);
        } catch (Exception failure) {
            server.halt(false);
            throw new IllegalStateException("Deposit extension audit failed", failure);
        } finally {
            activate(saved);
        }
    }

    private static void exercise(ServerLevel level, double scale) throws Exception {
        JsonObject root = GeomeConfig.globalBaseProfile().rootCopy();
        root.addProperty("manage_vanilla_ores", false);
        root.add("terrain_dimensions", new JsonObject());
        root.add("ore_source_policies", new JsonObject());
        JsonObject ores = new JsonObject();
        ores.add("depositprobe:controller", ore("minecraft:emerald_block", "depositprobe:deposit", scale));
        ores.add("depositprobe:ordinary", ore("minecraft:iron_block", "orespawn:standard", null));
        ores.add("depositprobe:independent", ore("minecraft:diamond_block", "depositprobe:independent", null));
        root.add("ores", ores);
        activate(WorldGeologyProfile.fromJson(root, WorldGeologyProfileManager.activeProfile()));
        LevelChunk chunk = level.getChunk(-40 - (int) (scale * 4), 40);
        var generator = level.getChunkSource().getGenerator();
        fill(chunk, Blocks.GRANITE.defaultBlockState());
        String granite = hash(chunk);
        OreSpawnOreGeneration.FEATURE.place(new FeaturePlaceContext<>(Optional.empty(), level, generator,
                RandomSource.create(42), chunk.getPos().getMiddleBlockPosition(24), NoneFeatureConfiguration.INSTANCE));
        require(granite.equals(hash(chunk)),
                "An exact stone host also matched granite");
        fill(chunk);
        OreSpawnOreGeneration.FEATURE.place(new FeaturePlaceContext<>(Optional.empty(), level, generator,
                RandomSource.create(42), chunk.getPos().getMiddleBlockPosition(24), NoneFeatureConfiguration.INSTANCE));
        int ordinary = 0, controller = 0, independent = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                for (int y = 8; y <= 48; y++) {
                    var block = chunk.getBlockState(cursor.set(x, y, z)).getBlock();
                    if (block == Blocks.IRON_BLOCK) ordinary++;
                    if (block == Blocks.EMERALD_BLOCK) controller++;
                    if (block == Blocks.DIAMOND_BLOCK) independent++;
                }
            }
        }
        require(scale == 0 ? ordinary == 0 : ordinary > 0, "Ordinary background scale was ignored: " + ordinary);
        require(controller > 0 && independent > 0, "Independent deposit channel was reduced");

        // Call the real extra-gold gate without height/biome placement modifiers.
        // Those modifiers belong to Minecraft and would obscure the gate decision.
        List<Holder<PlacedFeature>> extra = new ArrayList<>();
        extra.add(level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                .getHolderOrThrow(net.minecraft.resources.ResourceKey.create(Registries.PLACED_FEATURE,
                        ResourceLocation.parse("minecraft:ore_gold_extra"))));
        Method wrap = VanillaOreFeatureGate.class.getDeclaredMethod("wrapFeatureList", List.class);
        wrap.setAccessible(true);
        wrap.invoke(null, extra);
        fill(chunk);
        boolean placed = extra.getFirst().value().feature().value().place(level, generator,
                RandomSource.create(42), chunk.getPos().getMiddleBlockPosition(24));
        if (scale == 0 || scale == 1) require(placed == (scale == 1), "Extra badlands gold ignored the controller");
        System.out.println("DEPOSIT_EXTENSION scale=" + scale + " ordinary=" + ordinary
                + " controller=" + controller + " independent=" + independent + " extra_gold=" + placed);
    }

    private static JsonObject ore(String output, String channel, Double scale) {
        JsonObject ore = new JsonObject();
        ore.addProperty("block", "minecraft:gold_ore");
        ore.addProperty("material", "orespawn:gold");
        JsonArray outputs = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("block", output);
        entry.addProperty("weight", 1);
        outputs.add(entry);
        ore.add("outputs", outputs);
        JsonObject rule = new JsonObject();
        rule.addProperty("min_y", 20);
        rule.addProperty("max_y", 36);
        rule.addProperty("frequency", 16);
        rule.addProperty("quantity", 12);
        rule.addProperty("placement_channel", channel);
        if (scale != null) rule.addProperty("background_generation_scale", scale);
        JsonArray hosts = new JsonArray();
        hosts.add("minecraft:stone");
        rule.add("host_blocks", hosts);
        JsonObject dimensions = new JsonObject();
        dimensions.add("minecraft:overworld", rule);
        ore.add("dimensions", dimensions);
        return ore;
    }

    private static void activate(WorldGeologyProfile profile) {
        try {
            Method method = WorldGeologyProfileManager.class.getDeclaredMethod("applyBenchmarkProfile", WorldGeologyProfile.class);
            method.setAccessible(true);
            method.invoke(null, profile);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Could not select disposable profile", failure);
        }
    }

    private static void fill(LevelChunk chunk) {
        fill(chunk, Blocks.STONE.defaultBlockState());
    }

    private static void fill(LevelChunk chunk, BlockState state) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                for (int y = 8; y <= 48; y++) chunk.setBlockState(cursor.set(x, y, z), state, false);
            }
        }
        chunk.setUnsaved(true);
    }

    private static String hash(LevelChunk chunk) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = chunk.getPos().getMinBlockX(); x <= chunk.getPos().getMaxBlockX(); x++) {
            for (int z = chunk.getPos().getMinBlockZ(); z <= chunk.getPos().getMaxBlockZ(); z++) {
                for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
                    int id = net.minecraft.world.level.block.Block.getId(chunk.getBlockState(cursor.set(x, y, z)));
                    digest.update((byte) (id >>> 24)); digest.update((byte) (id >>> 16));
                    digest.update((byte) (id >>> 8)); digest.update((byte) id);
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static void write(Path path, JsonObject root) throws Exception {
        Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
