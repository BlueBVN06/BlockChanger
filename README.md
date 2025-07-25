# 🧱 BlockChanger

<div align="center">
  <strong>Ultra-fast block snapshotting and restoration library for Bukkit/Spigot/Paper plugins.</strong><br>
  Efficient, high-performance, and easy to use.
</div>

---

## 🚀 Features

* 📸 **Fast Block Snapshotting** — Capture block data in a cuboid area with minimal overhead.
* ♻️ **Instant Restoration** — Restore block snapshots to their original state efficiently.
* ⚡ **High Performance** — Designed for speed with minimal memory usage.
* 🛆 **Lightweight API** — Simple to integrate and use in any Minecraft plugin.

---

## 🛠️ Setup

### 🔧 Build (Locally)

To build the project using Maven:

```bash
mvn clean install
```

### 📥 Add as a Dependency

Include BlockChanger in your Maven project:

```xml
<dependency>
    <groupId>dev.lrxh</groupId>
    <artifactId>BlockChanger</artifactId>
    <version>{latest-version}</version>
    <scope>provided</scope>
</dependency>
```

> Replace `{latest-version}` with the version shown in your repository or artifact manager.

---

## 📚 Usage

### 🧱 Create and Restore a Snapshot

```java
// Define two locations to form a cuboid
Location loc1 = ...;
Location loc2 = ...;

// Create a snapshot of the block states in the region
CuboidSnapshot snapshot = new CuboidSnapshot(loc1, loc2);

// Perform operations that modify the blocks...
// ...

// Restore the blocks in the cuboid to their original snapshot state
snapshot.restore();
``

## 📄 API Overview

```java
// Constructor
CuboidSnapshot snapshot = new CuboidSnapshot(Location corner1, Location corner2);

// Restore blocks
snapshot.restore();

// Optionally clear internal data if no longer needed
snapshot.clear();
```