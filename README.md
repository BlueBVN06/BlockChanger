# BlockChanger
<div align="center">
  
</div>
A single class that offers very fast block placing, high performance and allowing multiple versions (1.8 - 1.21.4)
  
More Information can be found at: https://www.spigotmc.org/threads/methods-for-changing-massive-amount-of-blocks-up-to-14m-blocks-s.395868/

## Setup
Just put the [BlockChanger](https://github.com/Devlrxxh/BlockChanger/blob/master/src/main/java/dev/lrxh/nms/blockChanger/BlockChanger.java) class in your project  
## Usage
```java
BlockChanger blockChanger = new BlockChanger(main, false);

World world = ...;
Location location = ...;
ItemStack item = new ItemStack(Material.GOLD_BLOCK);
Map<Location, ItemStack> blocks = new HashMap<>();
blocks.put(location, item);

blockChanger.setBlocks(world, blocks);

BlockData blockData = blockChanger.getBlockDataAt(location); // Get Block at a location ONLY SUPPORT FOR 1.16+

blockChanger. // see all available methods
``` 
### Snapshot System
```java
Location pos1 = ...;
Location pos2 = ...;

// Works for all supported versions
BlockChanger.Snapshot snapshot = blockChanger.capture(pos1, pos2);

blockChanger.revert(snapshot);
``` 
