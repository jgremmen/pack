# Pack – Compact Bit-Level Binary Streams for Java

[![License](https://img.shields.io/github/license/jgremmen/pack)](https://www.apache.org/licenses/LICENSE-2.0)
[![Maven Central](https://img.shields.io/maven-central/v/de.sayayi.lib/pack)](https://central.sonatype.com/artifact/de.sayayi.lib/pack)
[![Java](https://img.shields.io/badge/Java-21%2B-blue)](https://openjdk.org/projects/jdk/21/)

**Pack** is a lightweight Java library for reading and writing data in a compact bit-level binary
format. It supports configurable headers with magic bytes, version ranges, optional GZIP
compression, and a variety of data types — all packed using the smallest possible number of bits.

## Features

- **Bit-level I/O** – read and write booleans, enums, integers, longs, unsigned shorts, and strings
  at the bit level for maximum compactness
- **Configurable header** – define custom magic bytes, a version range, and compression support via
  a builder API
- **GZIP compression** – optionally compress the payload transparently
- **Variable-width encoding** – small values (0–255) are encoded with fewer bits using a
  variable-width scheme
- **Compact string encoding** – strings are written in a modified UTF-8 format with a compact length
  prefix
- **File type detection** – abstract base classes for the Java NIO `FileTypeDetector` SPI and
  Apache Tika `Detector` API
- **JPMS ready** – ships as module `de.sayayi.lib.pack`

## Requirements

- Java 21 or later

## Installation

### Gradle

```kotlin
dependencies {
  implementation("de.sayayi.lib:pack:0.2.0")

  // Optional: only required for Apache Tika detector support
  implementation("org.apache.tika:tika-core:2.9.2")
}
```

### Maven

```xml
<dependency>
  <groupId>de.sayayi.lib</groupId>
  <artifactId>pack</artifactId>
  <version>0.2.0</version>
</dependency>

<!-- Optional: only required for Apache Tika detector support -->
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-core</artifactId>
  <version>2.9.2</version>
  <optional>true</optional>
</dependency>
```

## Quick Start

### Define a pack configuration

```java
var config = new PackConfig.Builder()
    .withMagic("MYPACK")
    .withVersionRange(1, 10)
    .withCompressionSupport()
    .build();
```

### Write data

```java
try(var out = new PackOutputStream(config, /* version */ 3, /* compress */ true, outputStream)) {
  out.writeBoolean(true);
  out.writeEnum(RetentionPolicy.RUNTIME);
  out.writeInt(42);
  out.writeString("Hello, Pack!");
  out.writeSmallVar(200);
  out.writeLong(System.currentTimeMillis());
}
```

### Read data

```java
try(var in = new PackInputStream(config, inputStream)) {
  boolean flag    = in.readBoolean();
  var     policy  = in.readEnum(RetentionPolicy.class);
  int     number  = in.readInt();
  String  text    = in.readString();
  int     small   = in.readSmallVar();
  long    time    = in.readLong();

  in.getVersion().ifPresent(v -> System.out.println("Pack version: " + v));
  System.out.println("Compressed: " + in.isCompressed());
}
```

### Simple mode (no magic, no versioning)

For quick prototyping you can use the simplified constructors which default to compression support
only:

```java
// write
try(var out = new PackOutputStream(/* compress */ true, outputStream)) {
  out.writeString("compact data");
}

// read
try(var in = new PackInputStream(inputStream)) {
  String data = in.readString();
}
```

## Supported Data Types

| Method                  | Bits                | Description                                            |
|-------------------------|---------------------|--------------------------------------------------------|
| `writeBoolean`          | 1                   | Single boolean flag                                    |
| `writeEnum`             | auto or explicit    | Enum ordinal; bit width derived from constant count    |
| `writeSmall`            | 1–8 (fixed)         | Small unsigned value (0–255) with explicit bit width   |
| `writeSmallVar`         | 4–10 (variable)     | Small unsigned value (0–255) favoring smaller values   |
| `writeLarge`            | 9–64 (fixed)        | Arbitrary value with explicit bit width                |
| `writeUnsignedShort`    | 16                  | Unsigned 16-bit value (0–65 535)                       |
| `writeInt`              | 32                  | Signed 32-bit integer                                  |
| `writeLong`             | 64                  | Signed 64-bit long                                     |
| `writeString`           | variable            | Nullable string in compact modified UTF-8              |

All write methods have corresponding `read` and `skip` counterparts on `PackInputStream`.

## Pack Configuration

`PackConfig.Builder` lets you tailor the binary format:

| Setting                  | Description                                                                   |
|--------------------------|-------------------------------------------------------------------------------|
| `withMagic(…)`           | Magic bytes (or ASCII string) written/validated at the start of every stream  |
| `withVersionRange(…)`    | Lowest and highest version; bit width is derived automatically                |
| `noVersion()`            | Disables versioning entirely                                                  |
| `withCompressionSupport` | Enables the compression flag in the header                                    |

## File Type Detection

The `de.sayayi.lib.pack.detector` package provides two abstract base classes for content-type
detection of pack files:

### Java NIO SPI

Extend `AbstractFileTypeDetector` and register it via
`META-INF/services/java.nio.file.spi.FileTypeDetector`:

```java
public class MyPackDetector extends AbstractFileTypeDetector 
{
  public MyPackDetector() {
    super(MY_PACK_CONFIG, "application/x-mypack");
  }
}
```

### Apache Tika

Extend `AbstractTikaDetector` (requires `tika-core` on the classpath):

```java
public class MyPackTikaDetector extends AbstractTikaDetector 
{
  public MyPackTikaDetector() {
    super(MY_PACK_CONFIG, "application/x-mypack");
  }
}
```

Both detectors return annotated MIME types with optional `version` and `compress` parameters, e.g.
`application/x-mypack;version=3;compress=true`.

## Building from Source

```bash
git clone https://github.com/jgremmen/pack.git
cd pack
./gradlew build
```

## License

This project is licensed under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0).

Copyright 2025 Jeroen Gremmen

