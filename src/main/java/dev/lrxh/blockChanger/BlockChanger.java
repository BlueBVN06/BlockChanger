package dev.lrxh.blockChanger;

import dev.lrxh.blockChanger.lighting.LightingService;
import dev.lrxh.blockChanger.snapshot.ChunkListener;
import dev.lrxh.blockChanger.snapshot.ChunkSectionSnapshot;
import dev.lrxh.blockChanger.snapshot.CuboidSnapshot;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
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
    ServerLevel level = craftChunk.getCraftWorld().getHandle();

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
    setSections(chunkAccess, newSections, level, true);
  }

  private static void setSections(ChunkAccess chunkAccess, LevelChunkSection[] newSections, ServerLevel level, boolean copy) {
    LevelChunkSection[] currentSections = chunkAccess.getSections();

    if (currentSections.length != newSections.length) {
      throw new IllegalArgumentException("Section count mismatch: expected "
        + currentSections.length + ", but got " + newSections.length);
    }

    IntStream.range(0, currentSections.length).parallel().forEach(i -> {
      LevelChunkSection section = currentSections[i];
      LevelChunkSection newSection = newSections[i];

      if (section == null) {
        section = createEmptySection(level);
      }

      if (newSection == null) {
        newSection = createEmptySection(level);
      }

      if (section.hasOnlyAir() && newSection.hasOnlyAir()) return;

      currentSections[i] = copy ? newSection.copy() : newSection;
    });
  }

  public static LevelChunkSection createEmptySection(Level level) {
    PalettedContainer<BlockState> states = new PalettedContainer<>(
      Block.BLOCK_STATE_REGISTRY,
      Blocks.AIR.defaultBlockState(),
      PalettedContainer.Strategy.SECTION_STATES,
      null
    );

    Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
    Holder<Biome> defaultBiome = biomeRegistry.getOrThrow(Biomes.PLAINS);

    PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(
      biomeRegistry.asHolderIdMap(),
      defaultBiome,
      PalettedContainer.Strategy.SECTION_BIOMES,
      null
    );

    return new LevelChunkSection(states, biomes);
  }

  public static CompletableFuture<Void> setBlocks(Map<Location, BlockData> blocks, boolean updateLighting) {
    return CompletableFuture.runAsync(() -> {
      if (blocks == null || blocks.isEmpty()) return;

      Chunk firstChunk = blocks.keySet().iterator().next().getChunk();
      ServerLevel level = ((CraftChunk) firstChunk).getCraftWorld().getHandle();

      int estimatedChunks = Math.max(4, blocks.size() / 64);
      Long2ObjectMap<List<Map.Entry<Location, BlockData>>> chunkMap =
        new Long2ObjectOpenHashMap<>(estimatedChunks);

      for (Map.Entry<Location, BlockData> e : blocks.entrySet()) {
        Location loc = e.getKey();
        long chunkKey = (((long) (loc.getBlockX() >> 4)) << 32) | ((loc.getBlockZ() >> 4) & 0xffffffffL);

        List<Map.Entry<Location, BlockData>> list = chunkMap.get(chunkKey);
        if (list == null) {
          list = new ArrayList<>(4);
          chunkMap.put(chunkKey, list);
        }
        list.add(e);
      }

      IdentityHashMap<BlockData, net.minecraft.world.level.block.state.BlockState> stateCache =
        new IdentityHashMap<>(blocks.size() / 4);

      List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>(chunkMap.size());

      for (Long2ObjectMap.Entry<List<Map.Entry<Location, BlockData>>> entry : chunkMap.long2ObjectEntrySet()) {
        long chunkKey = entry.getLongKey();
        List<Map.Entry<Location, BlockData>> chunkBlocks = entry.getValue();

        CompletableFuture<Chunk> task = CompletableFuture.supplyAsync(() -> {
          int chunkX = (int) (chunkKey >> 32);
          int chunkZ = (int) chunkKey;

          Chunk bukkitChunk = firstChunk.getWorld().getChunkAt(chunkX, chunkZ);
          ChunkAccess access = ((CraftChunk) bukkitChunk).getHandle(ChunkStatus.FULL);
          LevelChunkSection[] sections = access.getSections();

          boolean changed = false;

          for (Map.Entry<Location, BlockData> blockEntry : chunkBlocks) {
            Location loc = blockEntry.getKey();
            BlockData bd = blockEntry.getValue();

            net.minecraft.world.level.block.state.BlockState state =
              stateCache.computeIfAbsent(bd, k -> ((CraftBlockData) k).getState());

            int bx = loc.getBlockX();
            int by = loc.getBlockY();
            int bz = loc.getBlockZ();

            int sectionIndex = level.getSectionIndex(by);
            LevelChunkSection section = sections[sectionIndex];
            if (section == null) {
              section = new LevelChunkSection(
                level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BIOME)
              );
              sections[sectionIndex] = section;
            }

            int localX = bx & 15;
            int localY = by & 15;
            int localZ = bz & 15;

            section.setBlockState(localX, localY, localZ, state, false);
            changed = true;
          }

          return changed ? bukkitChunk : null;
        }, EXECUTOR);

        chunkFutures.add(task);
      }

      List<Chunk> changedChunks = chunkFutures.stream()
        .map(CompletableFuture::join)
        .filter(Objects::nonNull)
        .toList();

      if (updateLighting && !changedChunks.isEmpty()) {
        LightingService.updateLighting(new HashSet<>(changedChunks), true);
      } else {
        for (Chunk c : changedChunks) {
          c.getWorld().refreshChunk(c.getX(), c.getZ());
        }
      }

    }, EXECUTOR);
  }

  public static CompletableFuture<Void> updateLighting(Set<Chunk> chunks) {
    return CompletableFuture.runAsync(() -> LightingService.updateLighting(chunks, true), EXECUTOR);
  }

  public static CompletableFuture<Void> restoreCuboidSnapshotAsync(CuboidSnapshot snapshot, boolean clearEntities) {
    return CompletableFuture.runAsync(() -> restoreCuboidSnapshot(snapshot, clearEntities), EXECUTOR);
  }

  public static void restoreCuboidSnapshot(CuboidSnapshot snapshot, boolean clearEntities) {
    for (Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshot.getSnapshots().entrySet()) {
      restoreChunkBlockSnapshot(entry.getKey(), entry.getValue(), clearEntities);
    }

    LightingService.updateLighting(snapshot.getSnapshots().keySet(), true);
  }
}
