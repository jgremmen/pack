# Version [0.3.0](https://github.com/jgremmen/pack/tree/0.3.0) (2026-07-27)

## Breaking Changes

### Enum bit width calculation corrected

The `readEnum`, `writeEnum`, and `skipEnum` methods on `PackInputStream` and `PackOutputStream` previously used an
incorrect algorithm to determine the number of bits required to encode an enum value.

Pack streams written with version 0.2.0 (or earlier) that contain enum values may not be readable with the 0.3.0
`readEnum` method if the enum has a constant count where the old and new formulas produce different bit widths.

The old methods have been preserved as deprecated alternatives for backward compatibility:

- `PackInputStream.readEnumOld(Class)` (replaces the old `readEnum(Class)` behavior)
- `PackInputStream.skipEnumOld(Class)` (replaces the old `skipEnum(Class)` behavior)
- `PackOutputStream.writeEnumOld(Enum)` (replaces the old `writeEnum(Enum)` behavior)

These deprecated methods are marked for removal and should only be used to read legacy pack streams.

**Migration:** If you need to read pack streams created by version 0.2.0 or earlier, use the `*Old` variants.
For new pack streams, use the standard `readEnum`, `writeEnum`, and `skipEnum` methods.

```java
// Reading a legacy (pre-0.3.0) pack stream:
MyEnum value = inputStream.readEnumOld(MyEnum.class);

// Writing/reading a new pack stream (0.3.0+):
outputStream.writeEnum(MyEnum.SOME_VALUE);
MyEnum value = inputStream.readEnum(MyEnum.class);
```

### Compressed packs now use maximum compression level

`PackOutputStream` now uses `Deflater.BEST_COMPRESSION` (level 9) when writing compressed pack streams. Previously,
the default compression level was used. This produces smaller output but compression is slower. Existing compressed
pack streams remain fully readable; this change only affects newly written streams.

### Field visibility change in AbstractFileTypeDetector

In `AbstractFileTypeDetector`, the fields `packConfig` and `mimeType` have been changed from `private final` to
`protected final`. This is not a source-level breaking change for subclasses, but it is a binary-incompatible change.
Recompilation of subclasses is required.

### Dependency changes

| Dependency | Scope | Old version | New version |
|---|---|---|---|
| org.apache.tika:tika-core | compile | [1.19,3.3) | [1.19,3.4) |

## Bug Fixes

- Enum bit width calculation produced incorrect results for certain enum sizes (e.g., enums with 2 or 4 constants were encoded with one bit too many, while enums with 17 constants were encoded with one bit too few). The `readEnum`/`writeEnum`/`skipEnum` methods now correctly compute the minimum number of bits required.
