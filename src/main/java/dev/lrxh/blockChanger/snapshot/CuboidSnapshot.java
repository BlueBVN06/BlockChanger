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

  private CuboidSnapshot(Map<Chunk, ChunkSectionSnapshot> snapshots) {
    this.snapshots = Collections.unmodifiableMap(snapshots);
    snapshots.values().forEach(SnapshotService::addSnapshot);
  }

  public static CompletableFuture<CuboidSnapshot> create(Location pos1, Location pos2) {
    World world = pos1.getWorld();

    int minChunkX = Math.min(pos1.getChunk().getX(), pos2.getChunk().getX());
    int maxChunkX = Math.max(pos1.getChunk().getX(), pos2.getChunk().getX());
    int minChunkZ = Math.min(pos1.getChunk().getZ(), pos2.getChunk().getZ());
    int maxChunkZ = Math.max(pos1.getChunk().getZ(), pos2.getChunk().getZ());

    List<CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>>> futures = new java.util.ArrayList<>();

    for (int x = minChunkX; x <= maxChunkX; x++) {
      for (int z = minChunkZ; z <= maxChunkZ; z++) {
        CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>> future = world.getChunkAtAsync(x, z)
          .thenApplyAsync(chunk -> {
            ChunkSectionSnapshot snapshot = BlockChanger.createChunkBlockSnapshot(chunk);
            SnapshotService.addSnapshot(snapshot);
            return Map.entry(chunk, snapshot);
          });

        futures.add(future);
      }
    }

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> {
        Map<Chunk, ChunkSectionSnapshot> snapshotMap = new HashMap<>();
        for (CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>> future : futures) {
          Map.Entry<Chunk, ChunkSectionSnapshot> entry = future.join();
          snapshotMap.put(entry.getKey(), entry.getValue());
        }
        return new CuboidSnapshot(snapshotMap);
      });
  }

  public Map<Chunk, ChunkSectionSnapshot> getSnapshots() {
    return snapshots;
  }

  public CompletableFuture<Void> restoreAsync() {
    return BlockChanger.restoreCuboidSnapshotAsync(this);
  }

  public void restore() {
    BlockChanger.restoreCuboidSnapshot(this);
  }

  public CuboidSnapshot clone() {
    return new CuboidSnapshot(new HashMap<>(snapshots));
  }

  public CompletableFuture<CuboidSnapshot> offset(int xOffset, int zOffset) {
    return offset(xOffset, zOffset, new HashMap<>());
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

    List<CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>>> futureEntries = snapshots.entrySet().stream()
      .map(entry -> {
        Chunk originalChunk = entry.getKey();
        ChunkSectionSnapshot snapshot = entry.getValue();

        int newX = originalChunk.getX() + chunkOffsetX;
        int newZ = originalChunk.getZ() + chunkOffsetZ;
        ChunkPosition newPos = new ChunkPosition(newX, newZ);

        Chunk preloadedChunk = preloadedChunks.get(newPos);

        CompletableFuture<Chunk> chunkFuture;

        if (preloadedChunk != null) {
          chunkFuture = CompletableFuture.completedFuture(preloadedChunk);
        } else {
          chunkFuture = originalChunk.getWorld().getChunkAtAsync(newX, newZ);
        }

        return chunkFuture.thenApply(chunk -> Map.entry(chunk, snapshot));
      })
      .toList();

    return CompletableFuture.allOf(futureEntries.toArray(new CompletableFuture[0]))
      .thenApply(v -> {
        Map<Chunk, ChunkSectionSnapshot> offsetSnapshots = new HashMap<>(snapshots.size());
        for (CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>> future : futureEntries) {
          Map.Entry<Chunk, ChunkSectionSnapshot> entry = future.join();
          offsetSnapshots.put(entry.getKey(), entry.getValue());
          SnapshotService.addSnapshot(entry.getValue());
        }
        return new CuboidSnapshot(offsetSnapshots);
      });
  }
}
