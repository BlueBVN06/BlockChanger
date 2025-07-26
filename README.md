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

### 📥 Add as a Dependency

Include BlockChanger in your project using [JitPack](https://jitpack.io):

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
