package dev.lrxh.blockChanger.snapshot;

import dev.lrxh.blockChanger.BlockChanger;
import net.minecraft.world.level.ChunkPos;
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
    final QueuedChunkSnapshot queuedChunkSnapshot = SnapshotService.getSnapshot(
      new ChunkPos(event.getChunk().getX(), event.getChunk().getZ()));

    if (queuedChunkSnapshot == null)
      return;
    if (event.getChunk().getWorld().getName() != queuedChunkSnapshot.worldName())
      return;
    Bukkit.getScheduler().runTask(plugin, () -> BlockChanger.restoreChunkBlockSnapshot(event.getChunk(), queuedChunkSnapshot.snapshot(), true));
  }
}
