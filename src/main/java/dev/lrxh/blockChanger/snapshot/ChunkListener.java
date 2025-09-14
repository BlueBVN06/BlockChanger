package dev.lrxh.blockChanger.snapshot;

import dev.lrxh.blockChanger.BlockChanger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

public class ChunkListener implements Listener {
  @EventHandler
  public void onChunkLoad(ChunkLoadEvent event) {
    ChunkSectionSnapshot snapshot = SnapshotService.getSnapshot(
        new net.minecraft.world.level.ChunkPos(event.getChunk().getX(), event.getChunk().getZ()));
    if (snapshot != null) {
      BlockChanger.restoreChunkBlockSnapshot(event.getChunk(), snapshot, false);
    }
  }
}
