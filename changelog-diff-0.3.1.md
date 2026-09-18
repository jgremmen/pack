# Version [0.3.1](https://github.com/jgremmen/pack/tree/0.3.1) (2026-09-18)

## Breaking Changes

### `AbstractTikaDetector` is deprecated

`AbstractTikaDetector` is an abstraction over the Apache Tika detection API, and only ever worked against Apache
Tika 3. This version adds an equivalent abstraction for Apache Tika 4, `AbstractTika4Detector`, since the two Tika
major versions are not binary compatible with one another. To keep the naming symmetrical between both Tika
versions, the existing Tika 3 abstraction is now also available under the name `AbstractTika3Detector`.
`AbstractTikaDetector` itself is annotated `@Deprecated(since = "0.3.1")` and scheduled for removal in a future
release, in favor of `AbstractTika3Detector`. It still behaves exactly as before, so existing code keeps working
without changes.

**Migration:** replace direct use of `AbstractTikaDetector` with `AbstractTika3Detector` when working with Apache
Tika 3, or with `AbstractTika4Detector` when working with Apache Tika 4. `AbstractTika3Detector` extends
`AbstractTikaDetector` and requires no code changes beyond the base class reference.

```java
// before (0.3.0, still works but deprecated)
public class MyPackTikaDetector extends AbstractTikaDetector
{
  public MyPackTikaDetector() {
    super(MY_PACK_CONFIG, "application/x-mypack");
  }
}

// after (0.3.1, Apache Tika 3)
public class MyPackTikaDetector extends AbstractTika3Detector
{
  public MyPackTikaDetector() {
    super(MY_PACK_CONFIG, "application/x-mypack");
  }
}

// after (0.3.1, Apache Tika 4)
public class MyPackTikaDetector extends AbstractTika4Detector
{
  public MyPackTikaDetector() {
    super(MY_PACK_CONFIG, "application/x-mypack");
  }
}
```

### Widened optional Tika dependency range

The optional `org.apache.tika:tika-core` dependency published with this library now spans both Apache Tika 3 and
Apache Tika 4 releases. Consumers who did not pin an explicit Tika version and relied on dependency resolution
picking a version below 3.4 may now resolve a Tika 4 artifact instead. Since `AbstractTikaDetector` and
`AbstractTika3Detector` are only source and binary compatible with Apache Tika 3, projects that use either of these
two classes must explicitly declare a `tika-core` dependency in the `[1.19,4.0)` range, or switch to
`AbstractTika4Detector` together with a Tika 4 `tika-core` dependency.

| Dependency | Scope | Old version | New version |
|---|---|---|---|
| org.apache.tika:tika-core | compile (optional) | [1.19,3.4) | [1.19,5.0) |

## New Features

### `AbstractTika4Detector` for Apache Tika 4

A new class `AbstractTika4Detector` provides pack file detection support for Apache Tika 4. It offers the same usage
pattern as `AbstractTikaDetector`/`AbstractTika3Detector`: subclass it, supply a `PackConfig` and a base MIME type,
and register the subclass as a Tika detector service provider. Internally it applies a close shield to the input
stream during detection, since Tika 4 invalidates the stream's mark once closed.

```java
public class MyPackTikaDetector extends AbstractTika4Detector
{
  private static final PackConfig MY_PACK_CONFIG = new PackConfig.Builder()
      .withMagic("MYPK")
      .withVersionRange(1, 5)
      .withCompressionSupport()
      .build();

  public MyPackTikaDetector() {
    super(MY_PACK_CONFIG, "application/x-mypack");
  }
}
```

Register the detector the same way as before, by listing the fully qualified class name in a
`META-INF/services/org.apache.tika.detect.Detector` file.

### `AbstractTika3Detector` as the non-deprecated Tika 3 base class

`AbstractTika3Detector` provides the same behaviour as the now-deprecated `AbstractTikaDetector`, but under a name
that will remain supported going forward. It extends `AbstractTikaDetector` without adding new behaviour, so
existing `PackConfig` and MIME type constructor arguments carry over unchanged.

```java
public class MyPackTikaDetector extends AbstractTika3Detector
{
  private static final PackConfig MY_PACK_CONFIG = new PackConfig.Builder()
      .withMagic("MYPK")
      .withVersionRange(1, 5)
      .withCompressionSupport()
      .build();

  public MyPackTikaDetector() {
    super(MY_PACK_CONFIG, "application/x-mypack");
  }
}
```

Both `AbstractTika3Detector` and `AbstractTika4Detector` are published in the same
`de.sayayi.lib.pack.detector` package as before, so only the class actually present with a matching Apache Tika
version on the classpath at compile and runtime can be used.

## Bug Fixes

*There are no bug fixes in this version.*
