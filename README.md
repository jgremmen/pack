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
  implementation("de.sayayi.lib:pack:0.3.1")

  // Optional: only required for Apache Tika detector support (Tika 3.x or 4.x)
  implementation("org.apache.tika:tika-core:3.3.3")
}
```

### Maven

```xml
<dependency>
  <groupId>de.sayayi.lib</groupId>
  <artifactId>pack</artifactId>
  <version>0.3.1</version>
</dependency>

<!-- Optional: only required for Apache Tika detector support (Tika 3.x or 4.x) -->
<dependency>
  <groupId>org.apache.tika</groupId>
  <artifactId>tika-core</artifactId>
  <version>3.3.3</version>
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

### Header layout and compatibility

The header consists of the magic bytes, a compression flag (1 bit, only with compression support)
and the version number (only with a version range). The encoding of the version number depends on
the number of versions in the range:

| Versions in range | Version encoding                                          |
|-------------------|-----------------------------------------------------------|
| 1                 | not written                                               |
| 2–256             | variable width (4, 5 or 10 bits, as with `writeSmallVar`) |
| more than 256     | fixed width, `versionBits` bits                           |

The header layout is therefore part of the file format. Once files have been written, the version
range may only be extended within the same class: a single-version range can't get a version field
later, a range must not grow past 256 versions, and a range with more than 256 versions must keep
its bit width. Otherwise existing files are misread. If the format is expected to evolve, start
with a range that leaves room for future versions, for example `withVersionRange(1, 100)`.

## File Type Detection

The `de.sayayi.lib.pack.detector` package provides abstract base classes for content-type
detection of pack files. A detector identifies pack files by their magic bytes, so the pack
configuration passed to a detector must define them. Since 0.3.2, the constructors throw an
`IllegalArgumentException` otherwise.

Detectors hold no mutable state and can be used by multiple threads concurrently.

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

If the application is a named module on the module path, the `META-INF/services` file is
ignored. Declare the provider in `module-info.java` instead:

```java
provides java.nio.file.spi.FileTypeDetector with com.example.MyPackDetector;
```

### Apache Tika

The Tika detector API is binary incompatible between Tika 3 and Tika 4. The library therefore
contains one base class for each version, both in the same package and artifact. Extend the class
that matches the `tika-core` version on your classpath:

| Tika version | Base class              |
|--------------|-------------------------|
| 3.x          | `AbstractTika3Detector` |
| 4.x          | `AbstractTika4Detector` |

```java
public class MyPackTikaDetector extends AbstractTika3Detector
{
  public MyPackTikaDetector() {
    super(MY_PACK_CONFIG, "application/x-mypack");
  }
}
```

Register the detector via `META-INF/services/org.apache.tika.detect.Detector`. `AbstractTikaDetector`
is deprecated since 0.3.1 and will be removed; extend `AbstractTika3Detector` instead.

Tika reads `META-INF/services` files itself and ignores `provides` declarations, so the file is
also needed in a named module. In that case, the package containing the detector must be exported,
at least to `org.apache.tika.core`:

```java
exports com.example to org.apache.tika.core;
```

All detectors return annotated MIME types with optional `version` and `compress` parameters, e.g.
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

