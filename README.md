# BlockChanger
<div align="center">
  
</div>
A single class that offers very fast block placing, high performance and allowing multiple versions (1.8 - 1.21)
  
More Information can be found at: https://www.spigotmc.org/threads/methods-for-changing-massive-amount-of-blocks-up-to-14m-blocks-s.395868/

# Setup
Just put the [BlockChanger](https://github.com/Devlrxxh/BlockChanger/blob/master/src/main/java/dev/lrxh/blockChanger/BlockChanger.java) class in your project  
# Usage
```java
BlockChanger.load(this, false);

World world = ...;
Location location = ...;
List<BlockChanger.BlockSnapshot> blocks = new ArrayList<>();
blocks.add(new BlockChanger.BlockSnapshot(location, Material.GOLD_BLOCK);

BlockChanger.setBlocks(world, blocks);

BlockChanger. // see all available methods
``` 
## Snapshot System
```java
Location pos1 = ...;
Location pos2 = ...;
World world = ...;

BlockChanger.Snapshot snapshot = BlockChanger.capture(pos1, pos2, true);

BlockChanger.revert(snapshot);
```
## Pasting System
```java
BlockChanger.Snapshot snapshot = ...;

// Pasting the snapshot with an offset of X: 100 and Z: 0 and ignore all air blocks
BlockChanger.paste(snapshot, 100, 0, true);
``` 
