package dev.lrxh.blockChanger;

import dev.lrxh.blockChanger.lighting.LightingService;
import dev.lrxh.blockChanger.snapshot.ChunkSectionSnapshot;
import dev.lrxh.blockChanger.snapshot.CuboidSnapshot;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.block.data.CraftBlockData;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class BlockChanger {
  private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
  public static int MINOR_VERSION;

  public static ChunkSectionSnapshot createChunkBlockSnapshot(Chunk chunk) {
    ChunkAccess chunkAccess = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
    ChunkPos position = chunkAccess.getPos();

    LevelChunkSection[] sections = chunkAccess.getSections();

    List<LevelChunkSection> copiedSections = Arrays.stream(sections)
      .map(LevelChunkSection::copy)
      .toList();

    return new ChunkSectionSnapshot(copiedSections.toArray(new LevelChunkSection[0]), position);
  }

  public static void restoreChunkBlockSnapshot(Chunk chunk, ChunkSectionSnapshot snapshot) {
    ChunkAccess chunkAccess = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);

    LevelChunkSection[] currentSections = chunkAccess.getSections();
    LevelChunkSection[] newSections = snapshot.sections();

    if (currentSections.length != newSections.length) {
      throw new IllegalArgumentException("Section count mismatch: expected "
        + currentSections.length + ", but got " + newSections.length);
    }

    for (int i = 0; i < currentSections.length; i++) {
      currentSections[i] = newSections[i].copy();
    }
  }

  public static CompletableFuture<Void> setBlocks(Map<Location, BlockData> blocks, boolean updateLighting) {
    return CompletableFuture.runAsync(() -> {
      Map<ChunkPos, Pair<ChunkAccess, Chunk>> chunkCache = new HashMap<>();

      for (Map.Entry<Location, BlockData> entry : blocks.entrySet()) {
        Location loc = entry.getKey();
        BlockData data = entry.getValue();

        int chunkX = loc.getBlockX() >> 4;
        int chunkZ = loc.getBlockZ() >> 4;
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);

        Pair<ChunkAccess, Chunk> pair = chunkCache.get(pos);
        if (pair == null) {
          Chunk chunk = loc.getChunk();
          ChunkAccess chunkAccess = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
          pair = Pair.of(chunkAccess, chunk);
          chunkCache.put(pos, pair);
        }

        ChunkAccess handle = pair.left();
        BlockPos blockPos = new BlockPos(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        BlockState nmsData = ((CraftBlockData) data).getState();
        handle.setBlockState(blockPos, nmsData);
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

  public static CompletableFuture<Void> restoreCuboidSnapshotAsync(CuboidSnapshot snapshot) {
    return CompletableFuture.runAsync(() -> {
      restoreCuboidSnapshot(snapshot);
    }, EXECUTOR);
  }

  public static void restoreCuboidSnapshot(CuboidSnapshot snapshot) {
    for (Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshot.getSnapshots().entrySet()) {
      restoreChunkBlockSnapshot(entry.getKey(), entry.getValue());
    }

    LightingService.updateLighting(snapshot.getSnapshots().keySet(), true);
  }

  public static int getMinorVersion() {
    if (MINOR_VERSION != 0) {
      return MINOR_VERSION;
    }

    String version = Bukkit.getServer().getBukkitVersion().split("-")[0];
    String[] versionParts = version.split("\\.");

    MINOR_VERSION = (versionParts.length >= 2) ? Integer.parseInt(versionParts[1]) : 0;

    return MINOR_VERSION;
  }
}
