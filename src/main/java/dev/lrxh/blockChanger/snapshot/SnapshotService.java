package dev.lrxh.blockChanger.snapshot;

import net.minecraft.world.level.ChunkPos;
import org.bukkit.World;

import java.util.HashMap;

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
