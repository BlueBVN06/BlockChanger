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
import org.apache.logging.log4j.util.InternalApi;
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

@SuppressWarnings("unused")
public class BlockChanger {
  public static final ExecutorService EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();

  private static final int[][] SHIFT_CACHE = new int[16][];
  private static final long[][] MASK_CACHE = new long[16][];
  private static final PalettedContainer<BlockState> states = new PalettedContainer<>(
    Block.BLOCK_STATE_REGISTRY,
    Blocks.AIR.defaultBlockState(),
    PalettedContainer.Strategy.SECTION_STATES,
    null
  );

  public static void initialize(final JavaPlugin plugin) {
    plugin.getServer().getPluginManager().registerEvents(new ChunkListener(plugin), plugin);

    for (int bits = 1; bits <= 16; ++bits) {
      final int vp = 64 / bits;
      final int[] shifts = new int[vp];
      final long[] masks = new long[vp];
      final long mask = (1L << bits) - 1L;
      for (int p = 0; p < vp; ++p) {
        final int s = p * bits;
        shifts[p] = s;
        masks[p] = mask << s;
      }
      SHIFT_CACHE[bits - 1] = shifts;
      MASK_CACHE[bits - 1] = masks;
    }
  }

  /**
   * Create a snapshot of the block data for a whole chunk.
   * <p>
   * This copies each LevelChunkSection so the returned snapshot is safe to store
   * and later restore without being affected by further chunk changes.
   *
   * @param chunk the Bukkit chunk to snapshot
   * @return a {@link ChunkSectionSnapshot} containing copied sections and the chunk position
   */
  @InternalApi
  public static ChunkSectionSnapshot createChunkBlockSnapshot(final Chunk chunk) {
    final CraftChunk craftChunk = (CraftChunk) chunk;
    final ChunkAccess chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);
    final ChunkPos position = chunkAccess.getPos();
    final Level level = craftChunk.getCraftWorld().getHandle();

    final LevelChunkSection[] sections = chunkAccess.getSections();
    final LevelChunkSection[] copiedSections = new LevelChunkSection[sections.length];

    for (int i = 0; i < sections.length; i++) {
      final LevelChunkSection section = sections[i];
      copiedSections[i] = (section != null) ? section.copy() : createEmptySection(level);
    }

    return new ChunkSectionSnapshot(copiedSections, position);
  }

  /**
   * Asynchronously restore a chunk from a snapshot.
   * <p>
   * This will restore the chunk back to its state when it was copied If {@code clearEntities} is true,
   * non-player entities in the chunk will be removed and existing block-entity data cleared.
   *
   * @param chunk         the Bukkit chunk to restore
   * @param snapshot      snapshot to restore from
   * @param clearEntities true to clear non-player entities and block entities
   * @return a CompletableFuture that completes when the restore task finishes
   */
  @InternalApi
  public static CompletableFuture<Void> restoreChunkBlockSnapshot(final Chunk chunk, final ChunkSectionSnapshot snapshot,
                                                                  final boolean clearEntities) {
    return CompletableFuture.runAsync(() -> restoreChunkBlockSnapshotInternal(chunk, snapshot, clearEntities), EXECUTOR);
  }

  /**
   * Internal restore implementation. This is the synchronous work that performs the actual
   * section replacement and optional entity clearing. Intended to be called on a worker thread.
   *
   * @param chunk         the Bukkit chunk to restore
   * @param snapshot      snapshot to restore from
   * @param clearEntities whether to clear entities and block entities in the chunk
   */
  private static void restoreChunkBlockSnapshotInternal(final Chunk chunk, final ChunkSectionSnapshot snapshot, final boolean clearEntities) {
    final CraftChunk craftChunk = (CraftChunk) chunk;
    final ChunkAccess chunkAccess = craftChunk.getHandle(ChunkStatus.FULL);
    final ServerLevel level = craftChunk.getCraftWorld().getHandle();

    if (clearEntities) {
      chunkAccess.blockEntities.clear();

      final int chunkX = chunk.getX();
      final int chunkZ = chunk.getZ();

      for (final Entity entity : craftChunk.getCraftWorld().getHandle().moonrise$getEntityLookup().getAll()) {
        if (entity instanceof Player)
          continue;

        final int entityChunkX = (int) Math.floor(entity.getX()) >> 4;
        final int entityChunkZ = (int) Math.floor(entity.getZ()) >> 4;

        if (entityChunkX == chunkX && entityChunkZ == chunkZ) {
          entity.remove(Entity.RemovalReason.DISCARDED);
        }
      }
    }

    final LevelChunkSection[] newSections = snapshot.sections();
    setSections(chunkAccess, newSections, level, true);
  }

  /**
   * Replace the sections in a {@link ChunkAccess} with the provided sections.
   * <p>
   * Any null sections are replaced with empty sections. If both current and new sections
   * are air-only, they are left untouched. This method validates section count and
   * performs the replacement in parallel for speed.
   *
   * @param chunkAccess the chunk to modify
   * @param newSections the sections to apply
   * @param level       server level used to create empty sections when needed
   * @param copy        if true, copy the provided new sections before applying them
   * @throws IllegalArgumentException if the provided sections array length differs from the current
   */
  private static void setSections(final ChunkAccess chunkAccess, final LevelChunkSection[] newSections, final ServerLevel level, final boolean copy) {
    final LevelChunkSection[] currentSections = chunkAccess.getSections();

    if (currentSections.length != newSections.length) {
      throw new IllegalArgumentException("Section count mismatch: expected "
        + currentSections.length + ", but got " + newSections.length);
    }

    IntStream.range(0, currentSections.length).parallel().forEach(i -> {
      LevelChunkSection section = currentSections[i];
      LevelChunkSection newSection = newSections[i];

      if (section == null) section = createEmptySection(level);

      if (newSection == null) newSection = createEmptySection(level);

      if (section.hasOnlyAir() && newSection.hasOnlyAir()) return;

      currentSections[i] = copy ? newSection.copy() : newSection;
    });
  }

  /**
   * Create an empty chunk section with default air blocks and default biome.
   * <p>
   * This helper builds the state and biome paletted containers used by LevelChunkSection.
   *
   * @param level level used to obtain biome registry and default holder
   * @return a new empty {@link LevelChunkSection}
   */
  private static LevelChunkSection createEmptySection(final Level level) {
    final Registry<Biome> biomeRegistry = level.registryAccess().lookupOrThrow(Registries.BIOME);
    final Holder<Biome> defaultBiome = biomeRegistry.getOrThrow(Biomes.PLAINS);

    final PalettedContainer<Holder<Biome>> biomes = new PalettedContainer<>(
      biomeRegistry.asHolderIdMap(),
      defaultBiome,
      PalettedContainer.Strategy.SECTION_BIOMES,
      null
    );

    return new LevelChunkSection(states, biomes);
  }

  /**
   * Low level writer that writes palette ids into a section's raw BitStorage.
   * <p>
   * This method packs the provided {@code paletteIds} into the section's BitStorage
   * using cached shift/mask tables. It performs a parallel write by collecting
   * per-thread masks/values and combining them in a final pass to avoid contention.
   *
   * @param section    target section to modify
   * @param indices    indices inside the section (0..4095) where values should be written
   * @param paletteIds palette ids corresponding to each index
   */
  private static void writePaletteIds(LevelChunkSection section, int[] indices, int[] paletteIds) {
    final int n = paletteIds.length;
    if (n == 0) return;

    final PalettedContainer<BlockState> container = section.states;
    final PalettedContainer.Data<BlockState> data = container.data;
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
    final int rawLen = raw.length;

    final long[] batchMasks = new long[rawLen];
    final long[] batchValues = new long[rawLen];

    for (int i = 0; i < n; i++) {
      final int idx = indices[i];
      final int pid = paletteIds[i] & (int) bitMask;
      final int cell = idx / valuesPerLong;
      final int pos = idx % valuesPerLong;
      batchMasks[cell] |= masks[pos];
      batchValues[cell] |= ((long) pid) << shifts[pos];
    }

    for (int j = 0; j < rawLen; j++) {
      raw[j] = (raw[j] & ~batchMasks[j]) | batchValues[j];
    }

    section.recalcBlockCounts();
  }

  /**
   * Set positions inside a section to the given block states.
   * <p>
   * This method resolves palette ids for each state in parallel and then calls
   * {@link #writePaletteIds} to update the packed storage.
   *
   * @param section section to modify
   * @param indices indices inside the section (0..4095)
   * @param states  block states to write at the corresponding indices
   */
  private static void setAll(LevelChunkSection section, int[] indices, BlockState[] states) {
    final int n = states.length;
    if (n == 0) return;

    final PalettedContainer<BlockState> container = section.states;
    final PalettedContainer.Data<BlockState> data = container.data;
    final Palette<BlockState> palette = data.palette();

    final int[] paletteIds = new int[n];
    final HashMap<BlockState, Integer> paletteCache = new HashMap<>(Math.min(n, 16));
    synchronized (palette) {
      for (int i = 0; i < n; i++) {
        final BlockState state = states[i];
        Integer id = paletteCache.get(state);
        if (id == null) {
          id = palette.idFor(state);
          paletteCache.put(state, id);
        }
        paletteIds[i] = id;
      }
    }

    writePaletteIds(section, indices, paletteIds);
  }

  /**
   * Asynchronously set a collection of block changes.
   * <p>
   * The map keys are {@link Location} objects and values are {@link BlockData}.
   * Changes are grouped per chunk, converted to native Minecraft {@link BlockState} and
   * written directly into chunk sections. Lighting is optionally updated after the changes.
   *
   * @param blocks         map of locations to block data to apply
   * @param updateLighting if true, run lighting updates for all affected chunks
   * @return a CompletableFuture that completes once the work and optional lighting updates begin
   */
  public static CompletableFuture<Void> setBlocks(final Map<Location, BlockData> blocks, final boolean updateLighting) {
    if (blocks == null || blocks.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    return CompletableFuture.runAsync(() -> {
      final Location firstLoc = blocks.keySet().iterator().next();
      final ServerLevel level = ((CraftChunk) firstLoc.getChunk()).getCraftWorld().getHandle();

      final int estimatedChunks = Math.max(4, blocks.size() >>> 6);
      final Long2ObjectOpenHashMap<List<Map.Entry<Location, BlockData>>> chunkMap =
        new Long2ObjectOpenHashMap<>(estimatedChunks);

      for (final Map.Entry<Location, BlockData> e : blocks.entrySet()) {
        final Location loc = e.getKey();
        final long chunkKey = (((long) (loc.getBlockX() >> 4)) << 32) | ((loc.getBlockZ() >> 4) & 0xFFFFFFFFL);
        chunkMap.computeIfAbsent(chunkKey, k -> new ArrayList<>(4)).add(e);
      }

      final IdentityHashMap<BlockData, net.minecraft.world.level.block.state.BlockState> stateCache =
        new IdentityHashMap<>(blocks.size() >>> 2);

      final List<CompletableFuture<Chunk>> chunkFutures = new ArrayList<>(chunkMap.size());

      for (final Long2ObjectMap.Entry<List<Map.Entry<Location, BlockData>>> entry : chunkMap.long2ObjectEntrySet()) {
        final long chunkKey = entry.getLongKey();
        final List<Map.Entry<Location, BlockData>> chunkBlocks = entry.getValue();

        final CompletableFuture<Chunk> task = CompletableFuture.supplyAsync(() -> {
          final int chunkX = (int) (chunkKey >> 32);
          final int chunkZ = (int) chunkKey;
          final Chunk bukkitChunk = firstLoc.getWorld().getChunkAt(chunkX, chunkZ);
          final ChunkAccess access = ((CraftChunk) bukkitChunk).getHandle(ChunkStatus.FULL);
          final LevelChunkSection[] sections = access.getSections();
          final int sectionCount = sections.length;

          final int[] sectionSizes = new int[sectionCount];
          for (final Map.Entry<Location, BlockData> blockEntry : chunkBlocks) {
            final int by = blockEntry.getKey().getBlockY();
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

          for (final Map.Entry<Location, BlockData> blockEntry : chunkBlocks) {
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

            final int idx = sectionSizes[sectionIndex];
            sectionIndices[sectionIndex][idx] = ((by & 15) << 8) | ((bz & 15) << 4) | (bx & 15);
            sectionStates[sectionIndex][idx] = state;
            sectionSizes[sectionIndex]++;
          }

          for (int s = 0; s < sectionCount; s++) {
            if (sectionIndices[s] == null) continue;
            setAll(sections[s], sectionIndices[s], sectionStates[s]);
          }

          return bukkitChunk;
        }, EXECUTOR);

        chunkFutures.add(task);
      }

      final List<Chunk> changedChunks = new ArrayList<>(chunkFutures.size());
      for (final CompletableFuture<Chunk> future : chunkFutures) {
        changedChunks.add(future.join());
      }

      if (updateLighting && !changedChunks.isEmpty()) {
        LightingService.updateLighting(new HashSet<>(changedChunks), true);
      } else {
        for (final Chunk c : changedChunks) {
          c.getWorld().refreshChunk(c.getX(), c.getZ());
        }
      }
    }, EXECUTOR);
  }

  /**
   * Request a lighting update for a set of chunks asynchronously.
   *
   * @param chunks set of chunks that need lighting recalculation
   * @return a CompletableFuture that completes once the lighting task is scheduled
   */
  public static CompletableFuture<Void> updateLighting(final Set<Chunk> chunks) {
    return CompletableFuture.runAsync(() -> LightingService.updateLighting(chunks, true), EXECUTOR);
  }

  /**
   * Asynchronously restore a saved cuboid snapshot.
   * <p>
   * This schedules the restore on the shared executor and will also trigger lighting updates
   * for all affected chunks after restoring.
   *
   * @param snapshot      cuboid snapshot containing multiple chunk snapshots
   * @param clearEntities whether to clear non-player entities when restoring
   * @return a CompletableFuture that completes when the restore begins
   */
  public static CompletableFuture<Void> restoreCuboidSnapshotAsync(final CuboidSnapshot snapshot, final boolean clearEntities) {
    return CompletableFuture.runAsync(() -> restoreCuboidSnapshot(snapshot, clearEntities), EXECUTOR);
  }

  /**
   * Restore all chunk snapshots contained in a {@link CuboidSnapshot} synchronously.
   * <p>
   * Each chunk snapshot will be restored and then lighting will be updated for all affected chunks.
   *
   * @param snapshot      snapshot to restore
   * @param clearEntities whether to clear non-player entities during restore
   */
  public static void restoreCuboidSnapshot(final CuboidSnapshot snapshot, final boolean clearEntities) {
    for (final Map.Entry<Chunk, ChunkSectionSnapshot> entry : snapshot.getSnapshots().entrySet()) {
      restoreChunkBlockSnapshot(entry.getKey(), entry.getValue(), clearEntities);
    }

    LightingService.updateLighting(snapshot.getSnapshots().keySet(), true);
  }
}
