package dev.lrxh.blockChanger;

import dev.lrxh.blockChanger.lighting.LightingService;
import dev.lrxh.blockChanger.snapshot.ChunkPosition;
import dev.lrxh.blockChanger.snapshot.ChunkSectionSnapshot;
import dev.lrxh.blockChanger.snapshot.CuboidSnapshot;
import dev.lrxh.blockChanger.utility.ReflectionUtility;
import dev.lrxh.blockChanger.wrapper.impl.chunk.CraftChunk;
import dev.lrxh.blockChanger.wrapper.impl.chunk.IChunkAccess;
import it.unimi.dsi.fastutil.Pair;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class BlockChanger {
    private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    public static int MINOR_VERSION;
    public static boolean isPaper;

    public static ChunkSectionSnapshot createChunkBlockSnapshot(Chunk chunk) {
        CraftChunk craftChunk = CraftChunk.from(chunk);
        ChunkPosition position = new ChunkPosition(chunk.getX(), chunk.getZ());
        return new ChunkSectionSnapshot(craftChunk.getHandle().getSectionsCopy(), position);
    }

    public static void restoreChunkBlockSnapshot(Chunk chunk, ChunkSectionSnapshot snapshot) {
        CraftChunk craftChunk = CraftChunk.from(chunk);
        craftChunk.getHandle().setSections(snapshot.sections());
    }

    public static CompletableFuture<Void> setBlocks(Map<Location, BlockData> blocks, boolean updateLighting) {
        return CompletableFuture.runAsync(() -> {
            Map<ChunkPosition, Pair<IChunkAccess, Chunk>> chunkCache = new HashMap<>();

            for (Map.Entry<Location, BlockData> entry : blocks.entrySet()) {
                Location loc = entry.getKey();
                BlockData data = entry.getValue();

                int chunkX = loc.getBlockX() >> 4;
                int chunkZ = loc.getBlockZ() >> 4;
                ChunkPosition pos = new ChunkPosition(chunkX, chunkZ);

                Pair<IChunkAccess, Chunk> pair = chunkCache.get(pos);
                if (pair == null) {
                    Chunk bukkitChunk = loc.getChunk();
                    CraftChunk craft = CraftChunk.from(bukkitChunk);
                    pair = Pair.of(craft.getHandle(), bukkitChunk);
                    chunkCache.put(pos, pair);
                }

                IChunkAccess handle = pair.left();
                handle.setBlock(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), data);
            }

            Set<Chunk> chunks = chunkCache.values().stream()
                    .map(Pair::right)
                    .collect(Collectors.toSet());

            if (updateLighting) {
                LightingService.updateLighting(chunks, true);
            } else {
                chunks.forEach(c -> c.getWorld().refreshChunk(c.getX(), c.getZ()));
            }

        }, EXECUTOR);
    }

    public static CompletableFuture<Void> updateLighting(Set<Chunk> chunks) {
        return CompletableFuture.runAsync(() -> LightingService.updateLighting(chunks, true), EXECUTOR);
    }

    public static CompletableFuture<Void> restoreCuboidSnapshot(CuboidSnapshot snapshot) {
        return CompletableFuture.runAsync(() -> {
            for (Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshot.getSnapshots().entrySet()) {
                restoreChunkBlockSnapshot(entry.getKey(), entry.getValue());
            }

            LightingService.updateLighting(snapshot.getSnapshots().keySet(), true);
        }, EXECUTOR);
    }

    public static int getMinorVersion() {
        if (MINOR_VERSION != 0) {
            return MINOR_VERSION;
        }

        String version = Bukkit.getServer().getBukkitVersion().split("-")[0];
        String[] versionParts = version.split("\\.");

        MINOR_VERSION = (versionParts.length >= 2) ? Integer.parseInt(versionParts[1]) : 0;

        isPaper = ReflectionUtility.getClass(
                "ca.spottedleaf.moonrise.patches.starlight.light.StarLightLightingProvider"
        ) != null;

        return MINOR_VERSION;
    }
}