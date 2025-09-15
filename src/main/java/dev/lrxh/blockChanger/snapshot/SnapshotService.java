package dev.lrxh.blockChanger.snapshot;

import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;

import org.bukkit.World;

public class SnapshotService {
  private static final HashMap<ChunkPos, QueuedChunkSnapshot> chunkSnapshots = new HashMap<>();

  public static void addSnapshot(ChunkSectionSnapshot snapshot, World world) {
    chunkSnapshots.put(snapshot.position(), new QueuedChunkSnapshot(world.getName(), snapshot));
  }

  public static QueuedChunkSnapshot getSnapshot(ChunkPos position) {
    if (chunkSnapshots.containsKey(position)) {
      return chunkSnapshots.remove(position);
    }
    
    return null;
  }
}
