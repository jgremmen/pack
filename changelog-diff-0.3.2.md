# Version [0.3.2](https://github.com/jgremmen/pack/tree/0.3.2) (2026-11-10)

## Breaking Changes

### Stricter argument validation in bit-level read and write methods

The bit-level methods of `PackOutputStream` and `PackInputStream` now validate their arguments and throw an
`IllegalArgumentException` for values outside the documented ranges. Previously, such arguments were accepted and
silently produced truncated or corrupted data.

`PackOutputStream.writeSmall(int, int)` rejects a bit width outside `1..8` as well as negative values and values that
do not fit in the given bit width. `writeLarge(long, int)` rejects a bit width outside `9..64` and, for bit widths
below 64, values that do not fit in the given bit width. `writeSmallVar(int)` rejects values outside `0..255`.

On the reading side, `PackInputStream.readSmall(int)` rejects a bit width outside `1..8`, `readLarge(int)` rejects a
bit width outside `9..64`, and `skip(int)` rejects a negative bit width.

Code that relied on the previous lenient behavior must make sure that values fit into the requested bit width before
writing them. Note that `writeInt(int)` accepts all `int` values, including negative ones, so it is the appropriate
method for signed 32-bit values.

### Tika and file type detectors require magic bytes

The constructors of `AbstractFileTypeDetector`, `AbstractTika3Detector`, `AbstractTika4Detector` and the deprecated
`AbstractTikaDetector` now throw an `IllegalArgumentException` if the given `PackConfig` does not define magic bytes.
Without magic bytes, almost any input would be recognized as a pack file, so such a detector would claim content that
does not belong to it. A `null` pack configuration is rejected with a `NullPointerException`.

Detector subclasses must use a configuration that defines magic bytes:

```java
private static final PackConfig MY_PACK_CONFIG =
    new PackConfig.Builder()
        .withMagic("MYPK")
        .withCompressionSupport()
        .build();
```

### `compress` parameter omitted for formats without compression support

The MIME type returned by the detectors only contains the `compress` parameter if the pack configuration supports
compression. For a configuration without compression support, `AbstractFileTypeDetector` previously returned for
example `application/x-mypack;version=3;compress=false` and now returns `application/x-mypack;version=3`. The Tika
detectors behave the same way for the returned `MediaType`.

Code that compares the detected MIME type with a fixed string must be adjusted accordingly. Subclasses overriding
`buildAnnotatedMimeType(OptionalInt, Boolean)` receive `null` for the `compressed` argument in this case, as already
documented for that method.

### Runtime exceptions are propagated by detectors

`AbstractFileTypeDetector.probeContentType(Path)` and the `detect` methods of the Tika detectors previously swallowed
every exception and reported the input as not recognized. Now, only `IOException`s (an unreadable file or input that
is not a valid pack stream) are handled this way. Runtime exceptions, for example thrown by an overridden
`buildAnnotatedMimeType(OptionalInt, Boolean)`, indicate a programming error and are propagated to the caller.

### `AbstractTikaDetector` is deprecated for removal

`AbstractTikaDetector`, deprecated since 0.3.1, is now annotated `@Deprecated(since = "0.3.1", forRemoval = true)`.
Compilers report a removal warning instead of a deprecation warning for code that still uses it. Extend
`AbstractTika3Detector` for Apache Tika 3 or `AbstractTika4Detector` for Apache Tika 4 instead; both are drop-in
replacements requiring only a change of the base class.

## New Features

### Flushing a `PackOutputStream`

`PackOutputStream` now implements `java.io.Flushable` and provides a `flush()` method. It passes all complete bytes
written so far to the underlying output stream and flushes that stream. If the payload is compressed, the compressor
is flushed with `Deflater.SYNC_FLUSH`, so a receiver can decompress all data written up to that point while the
stream stays open. This allows pack streams to be used for incremental transfers, for example over a socket.

```java
try(var out = new PackOutputStream(config, true, socketOut))
{
  for(var message: messages)
  {
    out.writeString(message.text());
    out.writeInt(message.id());
    out.flush();
  }
}
```

The bits of a partially written byte stay buffered until further writes complete the byte or the stream is closed.
Each flush of a compressed stream slightly reduces the compression ratio.

### More descriptive header errors

`PackInputStream` now reports header problems with the expected and the actual magic bytes in hexadecimal notation.
A stream that ends before the magic bytes are complete is reported with an `EOFException`, so it can be told apart
from a stream with wrong magic bytes, which is still reported with an `IOException`.

## Bug Fixes

### Silently truncated values in write methods

Negative values passed to `PackOutputStream.writeSmall(int, int)`, values outside `0..255` passed to
`writeSmallVar(int)` and values exceeding the bit width passed to `writeLarge(long, int)` were silently truncated to
the available bits. Reading the stream back returned a different value without any indication of an error. These
arguments are now rejected before anything is written (see Breaking Changes).

### Partial header written for invalid `PackOutputStream` arguments

The `PackOutputStream` constructors checked the compression setting and the version number only after writing parts
of the header to the underlying stream. An `IllegalArgumentException` therefore left an incomplete header behind. Both
arguments are now validated before anything is written.

### `writeEnum` failed for enum constants with class bodies

`PackOutputStream.writeEnum(Enum)` and the deprecated `writeEnumOld(Enum)` determined the enum constants through the
class of the given value. For an enum constant with a class body, this is an anonymous subclass without enum
constants, so writing the value failed with a `NullPointerException`. The declaring enum class is now used instead.

### Tika 3 detector closed the probed stream

`AbstractTikaDetector` and `AbstractTika3Detector` closed the input stream passed to `detect` when probing a pack
stream. The stream belongs to the caller and must remain readable by subsequent detectors and parsers. The stream is
now shielded from being closed during detection.

### Mark limit too small for compressed streams in Tika detectors

The Tika detectors marked the input stream with a read limit that did not account for the full size of the header
when compression and a version number were used. For compressed streams, more bytes than reserved were read while
initializing decompression, so the stream could not be reset reliably afterwards. The mark limit now covers the worst
case header size.

### File handle leak in `AbstractFileTypeDetector`

If a file did not start with a valid pack header, `AbstractFileTypeDetector.probeContentType(Path)` did not close the
file it had opened. Probing many non pack files could therefore exhaust the available file handles. The file is now
closed in every case.

### Magic bytes of a `PackConfig` could be modified

`PackConfig` and `PackConfig.Builder` kept a reference to the byte array passed as magic bytes. Modifying that array
afterwards changed the magic bytes of the configuration, contrary to its immutability. The array is now copied.

### Malformed enum and string data not reported as `IOException`

`PackInputStream.readEnum` failed with an `ArrayIndexOutOfBoundsException` if the stream contained an ordinal not
valid for the enum type; it now throws an `IOException`. `readString()` consumed bytes beyond the declared string
length if a multi-byte sequence exceeded it, which silently misaligned all subsequent reads. It now throws a
`UTFDataFormatException` with a message describing the problem.
