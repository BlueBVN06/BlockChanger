package dev.lrxh.blockChanger.snapshot;

import dev.lrxh.blockChanger.BlockChanger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.CompletableFuture;

public class CuboidSnapshot {
    private final Map<Chunk, ChunkSectionSnapshot> snapshots;

    public CuboidSnapshot(Location pos1, Location pos2) {
        World world = pos1.getWorld();

        int minChunkX = Math.min(pos1.getChunk().getX(), pos2.getChunk().getX());
        int maxChunkX = Math.max(pos1.getChunk().getX(), pos2.getChunk().getX());
        int minChunkZ = Math.min(pos1.getChunk().getZ(), pos2.getChunk().getZ());
        int maxChunkZ = Math.max(pos1.getChunk().getZ(), pos2.getChunk().getZ());


        Map<Chunk, ChunkSectionSnapshot> temp = new HashMap<>();

        for (int x = minChunkX; x <= maxChunkX; x++) {
            for (int z = minChunkZ; z <= maxChunkZ; z++) {
                Chunk chunk = world.getChunkAt(x, z);
                ChunkSectionSnapshot snapshot = BlockChanger.createChunkBlockSnapshot(chunk);
                temp.put(chunk, snapshot);
            }
        }


        this.snapshots = Collections.unmodifiableMap(temp);
    }

    private CuboidSnapshot(Map<Chunk, ChunkSectionSnapshot> snapshots) {
        this.snapshots = Collections.unmodifiableMap(snapshots);
    }

    public Map<Chunk, ChunkSectionSnapshot> getSnapshots() {
        return snapshots;
    }

    public CompletableFuture<Void> restore() {
        return BlockChanger.restoreCuboidSnapshot(this);
    }

    public CuboidSnapshot clone() {
        return new CuboidSnapshot(new HashMap<>(snapshots));
    }

    public CompletableFuture<CuboidSnapshot> offset(int xOffset, int zOffset) {
        if (snapshots.isEmpty()) {
            return CompletableFuture.completedFuture(new CuboidSnapshot(Collections.emptyMap()));
        }

        int chunkOffsetX = xOffset >> 4;
        int chunkOffsetZ = zOffset >> 4;

        World world = snapshots.keySet().iterator().next().getWorld();

        List<CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>>> futureEntries = snapshots.entrySet().stream()
                .map(entry -> {
                    Chunk originalChunk = entry.getKey();
                    ChunkSectionSnapshot snapshot = entry.getValue();

                    int newChunkX = originalChunk.getX() + chunkOffsetX;
                    int newChunkZ = originalChunk.getZ() + chunkOffsetZ;

                    return world.getChunkAtAsync(newChunkX, newChunkZ).thenApplyAsync(newChunk ->
                            Map.entry(newChunk, snapshot)
                    );
                })
                .toList();

        return CompletableFuture.allOf(futureEntries.toArray(new CompletableFuture[0]))
                .thenApply(v -> {
                    Map<Chunk, ChunkSectionSnapshot> offsetSnapshots = new HashMap<>(snapshots.size());
                    for (CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>> future : futureEntries) {
                        Map.Entry<Chunk, ChunkSectionSnapshot> entry = future.join();
                        offsetSnapshots.put(entry.getKey(), entry.getValue());
                    }
                    return new CuboidSnapshot(offsetSnapshots);
                });
    }

}
