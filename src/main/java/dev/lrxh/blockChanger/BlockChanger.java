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
import java.util.stream.IntStream;

public class BlockChanger {
  public static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  public static void initialize(JavaPlugin plugin) {
    plugin.getServer().getPluginManager().registerEvents(new ChunkListener(plugin), plugin);
  }

  public static ChunkSectionSnapshot createChunkBlockSnapshot(Chunk chunk) {
    CraftChunk craftChunk = (CraftChunk) chunk;
    ChunkAccess chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);
    ChunkPos position = chunkAccess.getPos();

    LevelChunkSection[] sections = chunkAccess.getSections();
    LevelChunkSection[] copiedSections = new LevelChunkSection[sections.length];

    for (int i = 0; i < sections.length; i++) {
      LevelChunkSection section = sections[i];
      copiedSections[i] = (section != null) ? section.copy() : null;
    }

    return new ChunkSectionSnapshot(copiedSections, position);
  }


  public static CompletableFuture<Void> restoreChunkBlockSnapshotAsync(Chunk chunk, ChunkSectionSnapshot snapshot,
                                                                       boolean clearEntities) {
    return CompletableFuture.runAsync(() -> restoreChunkBlockSnapshot(chunk, snapshot, clearEntities), EXECUTOR);
  }

  public static void restoreChunkBlockSnapshot(Chunk chunk, ChunkSectionSnapshot snapshot, boolean clearEntities) {
    CraftChunk craftChunk = (CraftChunk) chunk;
    ChunkAccess chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);

    if (clearEntities) {
      chunkAccess.blockEntities.clear();

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

    IntStream.range(0, currentSections.length).parallel().forEach(i -> {
      LevelChunkSection section = currentSections[i];
      LevelChunkSection newSection = newSections[i];

      if ((section == null || section.hasOnlyAir()) &&
        (newSection == null || newSection.hasOnlyAir())) return;

      currentSections[i] = (copy && newSection != null) ? newSection.copy() : newSection;
    });
  }


  public static CompletableFuture<Void> setBlocks(Map<Location, BlockData> blocks, boolean updateLighting) {
    return CompletableFuture.runAsync(() -> {
      if (blocks == null || blocks.isEmpty()) return;

      int expectedChunks = Math.max(4, blocks.size() / 64);
      Map<Long, Pair<ChunkAccess, Chunk>> chunkCache = new HashMap<>(expectedChunks, 0.9f);

      Map<BlockData, List<Location>> grouped = new IdentityHashMap<>();
      for (Map.Entry<Location, BlockData> e : blocks.entrySet()) {
        grouped.computeIfAbsent(e.getValue(), k -> new ArrayList<>()).add(e.getKey());
      }

      BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

      for (Map.Entry<BlockData, List<Location>> entry : grouped.entrySet()) {
        BlockState nmsState = ((CraftBlockData) entry.getKey()).getState();

        for (Location loc : entry.getValue()) {
          int chunkX = loc.getBlockX() >> 4;
          int chunkZ = loc.getBlockZ() >> 4;
          long key = (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);

          Pair<ChunkAccess, Chunk> pair = chunkCache.computeIfAbsent(key, k -> {
            Chunk chunk = loc.getChunk();
            ChunkAccess access = ((CraftChunk) chunk).getHandle(ChunkStatus.FULL);
            return Pair.of(access, chunk);
          });

          mpos.set(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
          pair.left().setBlockState(mpos, nmsState);
        }
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
    return CompletableFuture.runAsync(() -> restoreCuboidSnapshot(snapshot, clearEntites), EXECUTOR);
  }

  public static void restoreCuboidSnapshot(CuboidSnapshot snapshot, boolean clearEntities) {
    for (Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshot.getSnapshots().entrySet()) {
      restoreChunkBlockSnapshot(entry.getKey(), entry.getValue(), clearEntities);
    }

    LightingService.updateLighting(snapshot.getSnapshots().keySet(), true);
  }
}
