## Version [0.1.1](https://github.com/jgremmen/pack/releases/tag/0.1.1) (2025-06-10)

### Bug Fixes

- Fixed `readInt()` and `readLong()` in `PackInputStream` returning incorrect values when the
  read position was not byte-aligned. Both methods now consistently delegate to `readLarge()`,
  which correctly handles arbitrary bit offsets.
- Fixed compression detection in `PackInputStream`: the constructor previously required
  `PackConfig.isCompressionSupport()` to return `true` in addition to the stream header
  indicating compression. The compression flag from the stream header is now honored
  unconditionally.
