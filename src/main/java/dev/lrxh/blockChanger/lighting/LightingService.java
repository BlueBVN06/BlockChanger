package dev.lrxh.blockChanger.lighting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Chunk;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class LightingService {
  public static void updateLighting(final Set<Chunk> chunks, final boolean refresh) {
    if (chunks == null || chunks.isEmpty()) return;

    final Chunk firstChunk = chunks.iterator().next();
    final ServerLevel world = ((org.bukkit.craftbukkit.CraftWorld) firstChunk.getWorld()).getHandle();
    final World bukkitWorld = firstChunk.getWorld();

    final List<ChunkPos> chunkPositions = new ArrayList<>(chunks.size());

    for (Chunk chunk : chunks) {
      chunkPositions.add(new ChunkPos(chunk.getX(), chunk.getZ()));
      if (refresh) {
        bukkitWorld.refreshChunk(chunk.getX(), chunk.getZ());
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
