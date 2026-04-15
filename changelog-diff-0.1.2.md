## Version [0.1.2](https://github.com/jgremmen/pack/releases/tag/0.1.2) (2025-06-14)

### New Features

- Added a convenience constructor `PackInputStream(InputStream)` that creates a pack input
  stream with a default configuration (compression support enabled, no magic bytes, no
  versioning). This allows reading a pack stream without explicitly building a `PackConfig`:

  ```java
  try(var in = new PackInputStream(inputStream)) {
    var value = in.readString();
  }
  ```

- Added a convenience constructor `PackOutputStream(boolean, OutputStream)` that creates a
  pack output stream with a default configuration. The `compress` parameter controls whether
  the payload is GZIP-compressed:

  ```java
  try(var out = new PackOutputStream(true, outputStream)) {
    out.writeString("hello");
  }
  ```

### Bug Fixes

- Fixed `readString()` in `PackInputStream` which could produce corrupt results or throw
  unexpected exceptions when reading strings containing multi-byte UTF-8 characters. The method
  now reads and decodes UTF-8 bytes directly from the underlying stream instead of bulk-reading
  into an intermediate buffer.
- Fixed compression handling in `PackOutputStream`: the constructor previously required
  `PackConfig.isCompressionSupport()` to return `true` before honoring the `compress` flag.
  The compression flag is now applied unconditionally, consistent with the `PackInputStream`
  fix in version 0.1.1.
- Fixed header magic validation in `PackInputStream` to use `readNBytes` instead of `read`,
  which ensures the full magic byte sequence is read even if the underlying stream returns
  partial data.
