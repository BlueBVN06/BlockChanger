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
import net.minecraft.util.BitStorage;
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
import net.minecraft.world.level.chunk.Palette;
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
  private static final int[][] SHIFT_CACHE = new int[16][];
  private static final long[][] MASK_CACHE = new long[16][];

  public static void initialize(JavaPlugin plugin) {
    plugin.getServer().getPluginManager().registerEvents(new ChunkListener(plugin), plugin);

    for (int bits = 1; bits <= 16; ++bits) {
      int vp = 64 / bits;
      int[] shifts = new int[vp];
      long[] masks = new long[vp];
      long mask = (1L << bits) - 1L;
      for (int p = 0; p < vp; ++p) {
        int s = p * bits;
        shifts[p] = s;
        masks[p] = mask << s;
      }
      SHIFT_CACHE[bits - 1] = shifts;
      MASK_CACHE[bits - 1] = masks;
    }
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

  private static void setAll(LevelChunkSection section, int[] indices, BlockState[] states) {
    final int n = states.length;
    if (n == 0) return;

    final PalettedContainer<BlockState> container = section.states;
    final PalettedContainer.Data<BlockState> data = container.data;
    final Palette<BlockState> palette = data.palette();
    final BitStorage storage = data.storage();
    final int bits = storage.getBits();
    if (bits == 0) {
      section.recalcBlockCounts();
      return;
    }

    final int valuesPerLong = 64 / bits;
    final int[] shifts = SHIFT_CACHE[bits - 1];
    final long[] masks = MASK_CACHE[bits - 1];
    final long[] raw = storage.getRaw();
    final long bitMask = (1L << bits) - 1L;

    final IdentityHashMap<BlockState, Integer> paletteCache = new IdentityHashMap<>(Math.max(16, n >>> 1));
    final int[] paletteIds = new int[n];
    BlockState lastState = null;
    int lastId = -1;

    for (int i = 0; i < n; i++) {
      final BlockState state = states[i];
      if (state == lastState) {
        paletteIds[i] = lastId;
      } else {
        Integer pid = paletteCache.get(state);
        if (pid == null) {
          pid = palette.idFor(state);
          paletteCache.put(state, pid);
        }
        paletteIds[i] = pid;
        lastState = state;
        lastId = pid;
      }
    }

    final long[] batchMasks = new long[raw.length];
    final long[] batchValues = new long[raw.length];

    for (int i = 0; i < n; i++) {
      final int idx = indices[i];
      final int pid = paletteIds[i] & (int) bitMask;
      final int cell = idx / valuesPerLong;
      final int pos = idx % valuesPerLong;
      batchMasks[cell] |= masks[pos];
      batchValues[cell] |= ((long) pid) << shifts[pos];
    }

    final int rawLen = raw.length;
    int i = 0;
    final int vectorSize = 4;
    while (i < rawLen) {
      int limit = Math.min(i + vectorSize, rawLen);
      for (int j = i; j < limit; j++) {
        raw[j] = (raw[j] & ~batchMasks[j]) | batchValues[j];
      }
      i += vectorSize;
    }

    section.recalcBlockCounts();
  }

  public static CompletableFuture<Void> setBlocks(Map<Location, BlockData> blocks, boolean updateLighting) {
    if (blocks == null || blocks.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.runAsync(() -> {
      final Location firstLoc = blocks.keySet().iterator().next();
      final ServerLevel level = ((CraftChunk) firstLoc.getChunk()).getCraftWorld().getHandle();

      final int estimatedChunks = Math.max(4, blocks.size() >>> 6);
      final Long2ObjectOpenHashMap<List<Map.Entry<Location, BlockData>>> chunkMap =
        new Long2ObjectOpenHashMap<>(estimatedChunks);

      for (Map.Entry<Location, BlockData> e : blocks.entrySet()) {
        final Location loc = e.getKey();
        final long chunkKey = (((long) (loc.getBlockX() >> 4)) << 32) | ((loc.getBlockZ() >> 4) & 0xFFFFFFFFL);
        chunkMap.computeIfAbsent(chunkKey, k -> new ArrayList<>(4)).add(e);
      }

      final IdentityHashMap<BlockData, net.minecraft.world.level.block.state.BlockState> stateCache =
        new IdentityHashMap<>(blocks.size() >>> 2);

      List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>(chunkMap.size());

      for (Long2ObjectMap.Entry<List<Map.Entry<Location, BlockData>>> entry : chunkMap.long2ObjectEntrySet()) {
        final long chunkKey = entry.getLongKey();
        final List<Map.Entry<Location, BlockData>> chunkBlocks = entry.getValue();

        CompletableFuture<Chunk> task = CompletableFuture.supplyAsync(() -> {
          final int chunkX = (int) (chunkKey >> 32);
          final int chunkZ = (int) chunkKey;
          final Chunk bukkitChunk = firstLoc.getWorld().getChunkAt(chunkX, chunkZ);
          final ChunkAccess access = ((CraftChunk) bukkitChunk).getHandle(ChunkStatus.FULL);
          final LevelChunkSection[] sections = access.getSections();
          final int sectionCount = sections.length;

          final int[] sectionSizes = new int[sectionCount];
          for (Map.Entry<Location, BlockData> blockEntry : chunkBlocks) {
            int by = blockEntry.getKey().getBlockY();
            sectionSizes[level.getSectionIndex(by)]++;
          }

          final int[][] sectionIndices = new int[sectionCount][];
          final BlockState[][] sectionStates = new BlockState[sectionCount][];
          for (int s = 0; s < sectionCount; s++) {
            if (sectionSizes[s] > 0) {
              sectionIndices[s] = new int[sectionSizes[s]];
              sectionStates[s] = new BlockState[sectionSizes[s]];
              sectionSizes[s] = 0;
            }
          }

          for (Map.Entry<Location, BlockData> blockEntry : chunkBlocks) {
            final Location loc = blockEntry.getKey();
            final BlockData bd = blockEntry.getValue();
            final net.minecraft.world.level.block.state.BlockState state =
              stateCache.computeIfAbsent(bd, k -> ((CraftBlockData) k).getState());

            final int bx = loc.getBlockX();
            final int by = loc.getBlockY();
            final int bz = loc.getBlockZ();
            final int sectionIndex = level.getSectionIndex(by);

            LevelChunkSection section = sections[sectionIndex];
            if (section == null) {
              section = createEmptySection(level);
              sections[sectionIndex] = section;
            }

            int idx = sectionSizes[sectionIndex];
            sectionIndices[sectionIndex][idx] = ((by & 15) << 8) | ((bz & 15) << 4) | (bx & 15);
            sectionStates[sectionIndex][idx] = state;
            sectionSizes[sectionIndex]++;
          }

          for (int s = 0; s < sectionCount; s++) {
            if (sectionIndices[s] != null) {
              setAll(sections[s], sectionIndices[s], sectionStates[s]);
            }
          }

          return bukkitChunk;
        }, EXECUTOR);

        chunkFutures.add(task);
      }

      List<Chunk> changedChunks = new ArrayList<>(chunkFutures.size());
      for (CompletableFuture<Chunk> future : chunkFutures) {
        changedChunks.add(future.join());
      }

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
