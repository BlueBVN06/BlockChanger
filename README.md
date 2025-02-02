# BlockChanger
<div align="center">
  
</div>
A single class that offers very fast block placing, high performance and allowing multiple versions (1.16.5 - 1.21.4)
  
More Information can be found at: https://www.spigotmc.org/threads/methods-for-changing-massive-amount-of-blocks-up-to-14m-blocks-s.395868/

## Setup
Just put the [BlockChanger](https://github.com/Devlrxxh/BlockChanger/blob/master/src/main/java/dev/lrxh/nms/blockChanger/BlockChanger.java) class in your project  
## Usage
```java
BlockChanger blockChanger = new BlockChanger(main, false);

Location location = ...;
BlockData blockData = Material.GOLD_BLOCK.createBlockData();

blockChanger.setBlock(location, blockData);

blockChanger. // see all available methods
``` 
### Snapshot System
```java
Location min = ...;
Location max = ...;
BlockChanger.Snapshot snapshot;

snapshot = blockChanger.capture(min, max);

blockChanger.revert(snapshot);
``` 
