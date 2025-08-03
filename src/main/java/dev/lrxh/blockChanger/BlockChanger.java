package dev.lrxh.blockChanger;

import dev.lrxh.blockChanger.lighting.LightingService;
import dev.lrxh.blockChanger.snapshot.ChunkPosition;
import dev.lrxh.blockChanger.snapshot.ChunkSectionSnapshot;
import dev.lrxh.blockChanger.snapshot.CuboidSnapshot;
import dev.lrxh.blockChanger.utility.ReflectionUtility;
import dev.lrxh.blockChanger.wrapper.impl.chunk.CraftChunk;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BlockChanger {
    private static final ExecutorService VIRTUAL_THREAD_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
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
        chunk.getWorld().refreshChunk(chunk.getX(), chunk.getZ());
    }

    public static CompletableFuture<Void> restoreCuboidSnapshot(CuboidSnapshot snapshot) {
        return CompletableFuture.runAsync(() -> {
            for (Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshot.getSnapshots().entrySet()) {
                restoreChunkBlockSnapshot(entry.getKey(), entry.getValue());
            }

            LightingService.updateLighting(snapshot.getSnapshots().keySet());
        }, VIRTUAL_THREAD_EXECUTOR);
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
