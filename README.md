# 🧱 BlockChanger

Ultra-fast block snapshotting and restoration library, Efficient, high-performance, and easy to use.<br>

---

## 🚀 Features

* 📸 **Fast Block Snapshotting** — Quickly take snapshots of block data without slowing things down
* ♻️ **Instant Restoration** — Restore snapshots back to their original state near instant
* ⚡ **High Performance** — Made for speed with minimal usage
* 🔁 **Fully Asynchronous** — Runs in the background without blocking other tasks
* 🛆 **Lightweight API** — Simple to integrate

---

## 🛠️ Setup

### 📥 Add as a Dependency

Include BlockChanger in your project using [JitPack](https://jitpack.io/#Devlrxxh/BlockChanger):

#### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>
```

**Add the dependency:**

```xml
<dependency>
    <groupId>com.github.Devlrxxh</groupId>
    <artifactId>BlockChanger</artifactId>
    <version>{latest-commit-hash}</version>
</dependency>
```

#### Gradle

1. **Add the JitPack repository to your `build.gradle`:**

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}
```

2. **Add the dependency:**

```groovy
dependencies {
    implementation 'com.github.Devlrxxh:BlockChanger:{latest-commit-hash}'
}
```

---

## 📚 Usage

### 📥 Setup

```java
BlockChanger.initialize(pluginInstance);
```

### 🧱 Create and Restore a Snapshot

```java
// Define two locations to form a cuboid
Location loc1 = ...;
Location loc2 = ...;

// Create a snapshot asynchronously
        CuboidSnapshot.create(loc1, loc2).thenAccept(snapshot -> {
        // Restore the blocks in the cuboid to their original snapshot state
        snapshot.restore();
});
```

### 🔄 Set Multiple Blocks At Once

```java
Map<Location, BlockData> blocks = new HashMap<>();

Location loc = ...;
BlockData blockData = Material.GOLD_BLOCK.createBlockData();

blocks.put(loc, blockData);

long startTime = System.currentTimeMillis();

// Second parameter is for whether lighting updates should be done
BlockChanger.setBlocks(blocks, false).thenAccept(unused -> {
    long endTime = System.currentTimeMillis();
    long duration = endTime - startTime;

    sender.sendMessage("Blocks placed successfully!");
    sender.sendMessage("Time taken: " + duration + " ms (" + (duration / 1000.0) + " seconds)");
});
```
