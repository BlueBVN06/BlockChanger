package dev.lrxh.blockChanger.lighting;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Chunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class LightingService {
  public static void updateLighting(Set<Chunk> chunks, boolean refresh) {
    if (refresh) {
      for (Chunk chunk : chunks) {
        chunk.getWorld().refreshChunk(chunk.getX(), chunk.getZ());
      }
    }

    ServerLevel world = ((org.bukkit.craftbukkit.CraftWorld) chunks.iterator().next().getWorld()).getHandle();

    Collection<ChunkPos> chunkPositions = new ArrayList<>();

    for (Chunk chunk : chunks) {
      chunkPositions.add(new ChunkPos(chunk.getX(), chunk.getZ()));
    }

    world.getChunkSource().getLightEngine().starlight$serverRelightChunks(chunkPositions,
      chunkPos -> {
      },
      value -> {
      }
    );
  }
}
