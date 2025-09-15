package dev.lrxh.blockChanger;

import dev.lrxh.blockChanger.lighting.LightingService;
import dev.lrxh.blockChanger.snapshot.ChunkListener;
import dev.lrxh.blockChanger.snapshot.ChunkSectionSnapshot;
import dev.lrxh.blockChanger.snapshot.CuboidSnapshot;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.craftbukkit.CraftChunk;
import org.bukkit.craftbukkit.block.data.CraftBlockData;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

public class BlockChanger {
  private static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  public static void initialize(JavaPlugin plugin) {
    plugin.getServer().getPluginManager().registerEvents(new ChunkListener(plugin), plugin);
  }

  public static ChunkSectionSnapshot createChunkBlockSnapshot(Chunk chunk) {
    CraftChunk craftChunk = (CraftChunk) chunk;
    ChunkAccess chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);
    ChunkPos position = chunkAccess.getPos();

    LevelChunkSection[] sections = chunkAccess.getSections();

    List<LevelChunkSection> copiedSections = Arrays.stream(sections)
      .map(LevelChunkSection::copy)
      .toList();

    return new ChunkSectionSnapshot(copiedSections.toArray(new LevelChunkSection[0]), position);
  }

  public static CompletableFuture<Void> restoreChunkBlockSnapshotAsync(Chunk chunk, ChunkSectionSnapshot snapshot,
                                                                       boolean clearEntities) {
    return CompletableFuture.runAsync(() -> {
      restoreChunkBlockSnapshot(chunk, snapshot, clearEntities);
    }, EXECUTOR);
  }

  public static void restoreChunkBlockSnapshot(Chunk chunk, ChunkSectionSnapshot snapshot, boolean clearEntities) {
    CraftChunk craftChunk = (CraftChunk) chunk;
    ChunkAccess chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);

    if (clearEntities) {
      int chunkX = chunk.getX();
      int chunkZ = chunk.getZ();

      for (Entity entity : craftChunk.getCraftWorld().getHandle().moonrise$getEntityLookup().getAll()) {
        if (entity instanceof Player)
          continue;

        int entityChunkX = (int) Math.floor(entity.getX()) >> 4;
        int entityChunkZ = (int) Math.floor(entity.getZ()) >> 4;

        if (entityChunkX == chunkX && entityChunkZ == chunkZ) {
          entity.remove(Entity.RemovalReason.DISCARDED);
        }
      }
    }

    LevelChunkSection[] newSections = snapshot.sections();
    setSections(chunkAccess, newSections, true);
  }

  private static void setSections(ChunkAccess chunkAccess, LevelChunkSection[] newSections, boolean copy) {
    LevelChunkSection[] currentSections = chunkAccess.getSections();

    if (currentSections.length != newSections.length) {
      throw new IllegalArgumentException("Section count mismatch: expected "
        + currentSections.length + ", but got " + newSections.length);
    }

    for (int i = 0; i < currentSections.length; i++) {
      LevelChunkSection section = currentSections[i];
      LevelChunkSection newSection = newSections[i];

      if (section.hasOnlyAir() && newSection.hasOnlyAir()) {
        continue;
      }

      currentSections[i] = copy ? newSection.copy() : newSection;
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

  public static CompletableFuture<Void> restoreCuboidSnapshotAsync(CuboidSnapshot snapshot, boolean clearEntites) {
    return CompletableFuture.runAsync(() -> {
      restoreCuboidSnapshot(snapshot, clearEntites);
    }, EXECUTOR);
  }

  public static void restoreCuboidSnapshot(CuboidSnapshot snapshot, boolean clearEntites) {
    for (Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshot.getSnapshots().entrySet()) {
      restoreChunkBlockSnapshot(entry.getKey(), entry.getValue(), clearEntites);
    }

    LightingService.updateLighting(snapshot.getSnapshots().keySet(), true);
  }
}
