package dev.lrxh.blockChanger.snapshot;

import dev.lrxh.blockChanger.BlockChanger;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class ChunkListener implements Listener {
  private final JavaPlugin plugin;

  public ChunkListener(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void onChunkLoad(ChunkLoadEvent event) {
    Bukkit.getScheduler().runTask(plugin, () -> {
      ChunkSectionSnapshot snapshot = SnapshotService.getSnapshot(
          new net.minecraft.world.level.ChunkPos(event.getChunk().getX(), event.getChunk().getZ()));
      if (snapshot != null) {
        BlockChanger.restoreChunkBlockSnapshotAsync(event.getChunk(), snapshot, true);
      }
    });
  }
}
