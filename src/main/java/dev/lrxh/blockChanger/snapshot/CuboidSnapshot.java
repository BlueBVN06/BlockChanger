package dev.lrxh.blockChanger.snapshot;

import dev.lrxh.blockChanger.BlockChanger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

        if (xOffset % 16 != 0 || zOffset % 16 != 0) {
            throw new IllegalArgumentException("Offsets must be multiples of 16.");
        }

        int chunkOffsetX = Math.floorDiv(xOffset, 16);
        int chunkOffsetZ = Math.floorDiv(zOffset, 16);

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

    public CompletableFuture<CuboidSnapshot> offset(int xOffset, int zOffset, Map<ChunkPosition, Chunk> preloadedChunks) {
        if (snapshots.isEmpty()) {
            return CompletableFuture.completedFuture(new CuboidSnapshot(Collections.emptyMap()));
        }

        if (xOffset % 16 != 0 || zOffset % 16 != 0) {
            throw new IllegalArgumentException("Offsets must be multiples of 16.");
        }

        int chunkOffsetX = xOffset / 16;
        int chunkOffsetZ = zOffset / 16;

        Map<Chunk, ChunkSectionSnapshot> offsetSnapshots = new HashMap<>(snapshots.size());

        for (Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshots.entrySet()) {
            Chunk originalChunk = entry.getKey();
            ChunkSectionSnapshot snapshot = entry.getValue();

            int newX = originalChunk.getX() + chunkOffsetX;
            int newZ = originalChunk.getZ() + chunkOffsetZ;
            ChunkPosition newPos = new ChunkPosition(newX, newZ);

            Chunk targetChunk = preloadedChunks.get(newPos);

            // Fallback to original chunk if preloaded chunk is not available
            if (targetChunk == null) targetChunk = originalChunk.getWorld().getChunkAt(newX, newZ);

            offsetSnapshots.put(targetChunk, snapshot);
        }

        return CompletableFuture.completedFuture(new CuboidSnapshot(offsetSnapshots));
    }


}
