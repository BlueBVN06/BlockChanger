# 🧱 BlockChanger

Ultra-fast block snapshotting and restoration library, Efficient, high-performance, and easy to use.<br>

---

## 🚀 Features

* 📸 **Fast Block Snapshotting** — Capture block data with minimal overhead
* ♻️ **Instant Restoration** — Restore block snapshots to their original state efficiently
* ⚡ **High Performance** — Made for speed with minimal usage
* 🛆 **Lightweight API** — Simple to integrate

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

> Replace `{latest-version}` with the version shown in pom.xml

---

## 📚 Usage

### 🧱 Create and Restore a Snapshot

```java
// Define two locations to form a cuboid
Location loc1 = ...;
Location loc2 = ...;

// Create a snapshot of the block states in the region
CuboidSnapshot snapshot = new CuboidSnapshot(loc1, loc2);

// Restore the blocks in the cuboid to their original snapshot state
snapshot.restore();
