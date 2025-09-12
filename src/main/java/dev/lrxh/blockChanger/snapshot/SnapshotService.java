package dev.lrxh.blockChanger.snapshot;

import net.minecraft.world.level.ChunkPos;

import java.util.HashMap;

public class SnapshotService {
  private static final HashMap<ChunkPos, ChunkSectionSnapshot> chunkSnapshots = new HashMap<>();

  public static void addSnapshot(ChunkSectionSnapshot snapshot) {
    chunkSnapshots.put(snapshot.position(), snapshot);
  }

  public static ChunkSectionSnapshot getSnapshot(ChunkPos position) {
    if (chunkSnapshots.containsKey(position)) {
      return chunkSnapshots.remove(position);
    }
    return null;
  }
}
