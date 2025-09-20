package dev.lrxh.blockChanger.snapshot;

import dev.lrxh.blockChanger.BlockChanger;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;

public class CuboidSnapshot {

  private final Map<Chunk, ChunkSectionSnapshot> snapshots;

  private CuboidSnapshot(Map<Chunk, ChunkSectionSnapshot> snapshots) {
    this.snapshots = Collections.unmodifiableMap(snapshots);

    snapshots.forEach((chunk, snapshot) -> SnapshotService.addSnapshot(snapshot, chunk.getWorld()));
  }

  public static CompletableFuture<CuboidSnapshot> create(Location pos1, Location pos2) {
    World world = pos1.getWorld();

    int minChunkX = Math.min(pos1.getChunk().getX(), pos2.getChunk().getX());
    int maxChunkX = Math.max(pos1.getChunk().getX(), pos2.getChunk().getX());
    int minChunkZ = Math.min(pos1.getChunk().getZ(), pos2.getChunk().getZ());
    int maxChunkZ = Math.max(pos1.getChunk().getZ(), pos2.getChunk().getZ());

    int totalChunks = (maxChunkX - minChunkX + 1) * (maxChunkZ - minChunkZ + 1);

    return loadChunksAndSnapshots(totalChunks, (x, z) -> world.getChunkAtAsync(x, z)
        .thenApplyAsync(chunk -> Map.entry(chunk, BlockChanger.createChunkBlockSnapshot(chunk)), BlockChanger.EXECUTOR),
      minChunkX, maxChunkX, minChunkZ, maxChunkZ
    );
  }

  private static CompletableFuture<CuboidSnapshot> loadChunksAndSnapshots(
    int totalChunks,
    BiFunction<Integer, Integer, CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>>> loader,
    int minChunkX, int maxChunkX, int minChunkZ, int maxChunkZ) {

    List<CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>>> futures = new ArrayList<>(totalChunks);

    for (int x = minChunkX; x <= maxChunkX; x++) {
      for (int z = minChunkZ; z <= maxChunkZ; z++) {
        futures.add(loader.apply(x, z));
      }
    }

    return combineFutures(futures);
  }

  private static CompletableFuture<CuboidSnapshot> combineFutures(
    List<CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>>> futures) {

    return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
      .thenApply(v -> {
        Map<Chunk, ChunkSectionSnapshot> result = new HashMap<>(futures.size(), 0.9f);
        for (CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>> future : futures) {
          Map.Entry<Chunk, ChunkSectionSnapshot> entry = future.join();
          result.put(entry.getKey(), entry.getValue());
        }
        return new CuboidSnapshot(result);
      });
  }

  public Map<Chunk, ChunkSectionSnapshot> getSnapshots() {
    return snapshots;
  }

  public CompletableFuture<Void> restoreAsync(boolean clearEntities) {
    return BlockChanger.restoreCuboidSnapshotAsync(this, clearEntities);
  }

  public void restore(boolean clearEntities) {
    BlockChanger.restoreCuboidSnapshot(this, clearEntities);
  }

  @Override
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

    List<CompletableFuture<Map.Entry<Chunk, ChunkSectionSnapshot>>> futures = new ArrayList<>(snapshots.size());
    Executor executor = BlockChanger.EXECUTOR;

    for (Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshots.entrySet()) {
      Chunk originalChunk = entry.getKey();
      ChunkSectionSnapshot snapshot = entry.getValue();

      int newX = originalChunk.getX() + chunkOffsetX;
      int newZ = originalChunk.getZ() + chunkOffsetZ;
      ChunkPosition newPos = new ChunkPosition(newX, newZ);

      CompletableFuture<Chunk> chunkFuture = Optional.ofNullable(preloadedChunks.get(newPos))
        .map(CompletableFuture::completedFuture)
        .orElseGet(() -> originalChunk.getWorld().getChunkAtAsync(newX, newZ));

      futures.add(chunkFuture.thenApplyAsync(chunk -> Map.entry(chunk, snapshot), executor));
    }

    return combineFutures(futures);
  }
}
