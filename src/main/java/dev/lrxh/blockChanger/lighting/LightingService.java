package dev.lrxh.blockChanger.lighting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Chunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class LightingService {
  public static void updateLighting(final Set<Chunk> chunks, final boolean refresh) {
    if (chunks == null || chunks.isEmpty()) {
      return;
    }

    final Iterator<Chunk> iterator = chunks.iterator();
    final Chunk firstChunk = iterator.next();
    final ServerLevel world = ((org.bukkit.craftbukkit.CraftWorld) firstChunk.getWorld()).getHandle();

    final Collection<ChunkPos> chunkPositions = new ArrayList<>(chunks.size());

    for (final Chunk chunk : chunks) {
      chunkPositions.add(new ChunkPos(chunk.getX(), chunk.getZ()));
      if (refresh) {
        chunk.getWorld().refreshChunk(chunk.getX(), chunk.getZ());
      }
    }

    world.getChunkSource().getLightEngine().starlight$serverRelightChunks(
      chunkPositions,
      chunkPos -> {
      },
      value -> {
      }
    );
  }

}
