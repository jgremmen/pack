/*
 * Copyright 2025 Jeroen Gremmen
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.sayayi.lib.pack;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.io.Closeable;
import java.io.Flushable;
import java.io.IOException;
import java.io.OutputStream;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

import static java.lang.Integer.*;
import static java.lang.Math.max;
import static java.util.zip.Deflater.BEST_COMPRESSION;


/**
 * Output stream for writing data in the pack binary format.
 * <p>
 * A pack output stream writes a header based on the provided {@link PackConfig} (magic bytes, compression flag, and
 * version number) and then exposes methods for writing various data types at the bit level. Data is written using the
 * smallest possible number of bits, making the format compact.
 * <p>
 * When compression is enabled, the payload following the header is wrapped in a {@link GZIPOutputStream GZIP} stream.
 * <p>
 * The stream must be {@linkplain #close() closed} after use to ensure all buffered bits are flushed to the underlying
 * output stream. Values must be read back with the corresponding methods of {@link PackInputStream}, in the same order
 * and with the same bit widths as they were written.
 * <p>
 * Instances are not thread safe.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see PackInputStream
 * @see PackConfig
 */
public class PackOutputStream implements Closeable, Flushable
{
  /** Stream the packed data is written to; replaced by a GZIP stream after the header, if compressed. */
  private @NotNull OutputStream stream;

  /** Position of the next bit to write within {@link #b}, from {@code 7} (most significant) down to {@code 0}. */
  private int bit = 7;

  /** Partially written byte, holding the bits not yet written to {@link #stream}. */
  private byte b;


  /**
   * Creates a new pack output stream with default configuration and the given compression setting.
   * <p>
   * The default configuration has compression support enabled, no magic bytes and no versioning. It matches the
   * configuration used by {@link PackInputStream#PackInputStream(java.io.InputStream)}.
   *
   * @param compress  {@code true} to enable GZIP compression for the payload
   * @param stream    the underlying output stream to write to, not {@code null}
   *
   * @throws IOException  if an I/O error occurs while writing the header
   *
   * @since 0.1.2
   */
  @Contract(mutates = "param2,io")
  public PackOutputStream(boolean compress, @NotNull OutputStream stream) throws IOException {
    this(new PackConfig.Builder().withCompressionSupport().build(), compress, stream);
  }


  /**
   * Creates a new pack output stream with the given configuration and version number.
   * <p>
   * The payload is compressed if the pack configuration supports compression.
   *
   * @param packConfig  pack configuration, not {@code null}
   * @param version     the version number to write to the header; ignored if versioning is disabled
   * @param stream      the underlying output stream to write to, not {@code null}
   *
   * @throws IOException               if an I/O error occurs while writing the header
   * @throws IllegalArgumentException  if the version number is outside the configured range
   */
  @Contract(mutates = "param3,io")
  public PackOutputStream(@NotNull PackConfig packConfig, @Range(from = 0, to = MAX_VALUE) int version,
                          @NotNull OutputStream stream) throws IOException {
    this(packConfig, version, packConfig.isCompressionSupport(), stream);
  }


  /**
   * Creates a new pack output stream with the given configuration, using the lowest configured version number.
   * <p>
   * The payload is compressed if the pack configuration supports compression.
   *
   * @param packConfig  pack configuration, not {@code null}
   * @param stream      the underlying output stream to write to, not {@code null}
   *
   * @throws IOException  if an I/O error occurs while writing the header
   */
  @Contract(mutates = "param2,io")
  public PackOutputStream(@NotNull PackConfig packConfig, @NotNull OutputStream stream) throws IOException {
    this(packConfig, packConfig.getLowestVersionNumber(), packConfig.isCompressionSupport(), stream);
  }


  /**
   * Creates a new pack output stream with the given configuration and compression setting, using the lowest configured
   * version number.
   *
   * @param packConfig  pack configuration, not {@code null}
   * @param compress    {@code true} to enable GZIP compression for the payload
   * @param stream      the underlying output stream to write to, not {@code null}
   *
   * @throws IOException               if an I/O error occurs while writing the header
   * @throws IllegalArgumentException  if compression is requested but not supported by the configuration
   */
  @Contract(mutates = "param3,io")
  public PackOutputStream(@NotNull PackConfig packConfig, boolean compress, @NotNull OutputStream stream)
      throws IOException {
    this(packConfig, packConfig.getLowestVersionNumber(), compress, stream);
  }


  /**
   * Creates a new pack output stream with full control over configuration, version number, and compression.
   *
   * @param packConfig  pack configuration, not {@code null}
   * @param version     the version number to write to the header; ignored if versioning is disabled
   * @param compress    {@code true} to enable GZIP compression for the payload
   * @param stream      the underlying output stream to write to, not {@code null}
   *
   * @throws IOException               if an I/O error occurs while writing the header
   * @throws IllegalArgumentException  if compression is requested but not supported by the configuration, or if the
   *                                   version number is outside the configured range
   */
  @Contract(mutates = "param4,io")
  public PackOutputStream(@NotNull PackConfig packConfig, @Range(from = 0, to = MAX_VALUE) int version,
                          boolean compress, @NotNull OutputStream stream) throws IOException
  {
    if (compress && !packConfig.isCompressionSupport())
      throw new IllegalArgumentException("Compression is not supported");

    var versionBits = packConfig.getVersionBits();
    if (versionBits != 0 &&
        (version < packConfig.getLowestVersionNumber() || version > packConfig.getHighestVersionNumber()))
      throw new IllegalArgumentException("Invalid version number: " + version);

    this.stream = stream;

    stream.write(packConfig.getMagic());

    if (packConfig.isCompressionSupport())
      writeBoolean(compress);

    if (versionBits != 0)
    {
      version -= packConfig.getLowestVersionNumber();

      if (versionBits <= 8)
        writeSmallVar(version);
      else
        writeLarge(version, versionBits);
    }

    if (compress)
    {
      forceByteAlignment();
      stream.flush();

      this.stream = new GZIPOutputStream(stream, 1024, true) {{
        def.setLevel(BEST_COMPRESSION);
      }};
    }
  }


  /**
   * Writes a boolean value to the output stream, using a single bit.
   *
   * @param value  boolean value to write
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeBoolean(boolean value) throws IOException
  {
    if (value)
      b |= (byte)(1 << bit);

    if (bit-- == 0)
    {
      stream.write(b);
      bit = 7;
      b = 0;
    }
  }


  /**
   * Writes an enumerated value to the output stream, using the specified number of bits for its ordinal.
   *
   * @param value     enumerated value to write, not {@code null}
   * @param bitWidth  number of bits to use for the enumerated value (1..16)
   * @param <T>       the enum type
   *
   * @throws IllegalArgumentException  if {@code bitWidth} is not in the range 1..16 or if the ordinal of the enumerated
   *                                   value exceeds the {@code bitWidth}
   * @throws IOException               if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  @SuppressWarnings("ConstantValue")
  public <T extends Enum<T>> void writeEnum(@NotNull T value, @Range(from = 1, to = 16) int bitWidth) throws IOException
  {
    if (bitWidth <= 0 || bitWidth > 16)
      throw new IllegalArgumentException("Invalid bitWidth: " + bitWidth);

    var ordinal = value.ordinal();
    if (ordinal >= (1 << bitWidth))
      throw new IllegalArgumentException("Ordinal of enum '" + value.name() + "' exceeds bitWidth");

    if (bitWidth <= 8)
      writeSmall(value.ordinal(), bitWidth);
    else
      writeLarge(value.ordinal(), bitWidth);
  }


  /**
   * Writes an enumerated value to the output stream, using the bit width calculation of versions prior to 0.3.0.
   * <p>
   * This method was named {@code writeEnum} in versions prior to 0.3.0 but has been renamed because it does not
   * calculate the bit width correctly. Use {@link #writeEnum(Enum)} instead, which correctly determines the minimum
   * number of bits required.
   * <p>
   * This method is only provided for backward pack compatibility. For new implementations, use
   * {@link #writeEnum(Enum)}.
   *
   * @param value  enumerated value to write, not {@code null}
   * @param <T>    the enum type
   *
   * @throws IOException  if an I/O error occurs
   *
   * @since 0.3.0
   *
   * @deprecated Use {@link #writeEnum(Enum)} instead, which correctly calculates the bit width.
   */
  @Deprecated(forRemoval = true, since = "0.3.0")
  @Contract(mutates = "this,io")
  @SuppressWarnings("DeprecatedIsStillUsed")
  public <T extends Enum<T>> void writeEnumOld(@NotNull T value) throws IOException
  {
    final var n = EnumConstants.count(value.getDeclaringClass());

    writeEnum(value, bitCount(n | (n >> 1) | (n >> 2) | (n >> 4) | (n >> 8)));
  }


  /**
   * Writes an enumerated value to the output stream. The bit width is automatically derived from the number of
   * constants in the enum type.
   * <p>
   * This method replaces the previous {@code writeEnum(Enum)} implementation (now available as
   * {@link #writeEnumOld(Enum)}) which did not calculate the bit width correctly.
   *
   * @param value  enumerated value to write, not {@code null}
   * @param <T>    the enum type
   *
   * @throws IOException  if an I/O error occurs
   *
   * @since 0.3.0
   */
  @Contract(mutates = "this,io")
  public <T extends Enum<T>> void writeEnum(@NotNull T value) throws IOException {
    writeEnum(value, max(SIZE - numberOfLeadingZeros(EnumConstants.count(value.getDeclaringClass()) - 1), 1));
  }


  /**
   * Writes an unsigned 16-bit value to the output stream.
   *
   * @param value  unsigned value (0..65535)
   *
   * @throws IllegalArgumentException  if {@code value} is not in the range 0..65535
   * @throws IOException               if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeUnsignedShort(@Range(from = 0, to = 65535) int value) throws IOException {
    writeLarge(value, 16);
  }


  /**
   * Writes a 32-bit integer value to the output stream, using exactly 32 bits.
   *
   * @param value  the integer value to write
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeInt(int value) throws IOException {
    writeLarge(value & 0xffff_ffffL, 32);
  }


  /**
   * Writes a 64-bit long value to the output stream, using exactly 64 bits.
   *
   * @param value  the long value to write
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeLong(long value) throws IOException {
    writeLarge(value, 64);
  }


  /**
   * Writes a string value, which may be {@code null}, to the output stream using a compact modified UTF-8 encoding.
   * <p>
   * The length of the string is written as a prefix whose size depends on the length, so short strings take up less
   * space. The encoded string must not exceed 65535 bytes; strings consisting of ASCII characters only (except U+0000)
   * use one byte per character.
   *
   * @param string  the string to write, or {@code null}
   *
   * @throws IllegalArgumentException  if the encoded string exceeds the maximum supported length of 65535 bytes
   * @throws IOException               if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void writeString(String string) throws IOException
  {
    if (string == null)
    {
      writeSmall(0, 2);
      return;
    }

    final var stringLength = string.length();
    var utfLength = 0;

    for(var i = 0; i < stringLength; i++)
    {
      final var c = string.charAt(i);

      utfLength += c >= 0x0001 && c <= 0x007F ? 1 : c > 0x07FF ? 3 : 2;
      if (utfLength > 0xffff)
        throw new IllegalArgumentException("String too large");
    }

    if (utfLength < 16)
      writeSmall(0b01_0000 | utfLength, 6);
    else if (utfLength < 256)
      writeLarge(0b10_0000_0000 | utfLength, 10);
    else
    {
      writeSmall(0b11, 2);
      writeLarge(utfLength, 16);
    }

    if (utfLength > 0)
    {
      forceByteAlignment();

      final var bytes = new byte[utfLength];

      for(int charIdx = 0, utfIdx = 0; charIdx < stringLength; charIdx++)
      {
        final var c = string.charAt(charIdx);

        if (c >= 0x0001 && c <= 0x007F)
          bytes[utfIdx++] = (byte)c;
        else if (c > 0x07FF)
        {
          bytes[utfIdx++] = (byte)(0xE0 | ((c >> 12) & 0x0F));
          bytes[utfIdx++] = (byte)(0x80 | ((c >>  6) & 0x3F));
          bytes[utfIdx++] = (byte)(0x80 | ( c        & 0x3F));
        }
        else
        {
          bytes[utfIdx++] = (byte)(0xC0 | ((c >> 6) & 0x1F));
          bytes[utfIdx++] = (byte)(0x80 | ( c       & 0x3F));
        }
      }

      stream.write(bytes);
    }
  }


  /**
   * Writes a small unsigned value (0..255) using a variable-width encoding that favors smaller values.
   * <p>
   * Values 0..7 take 4 bits, values 8..15 take 5 bits and all other values take 10 bits.
   *
   * @param value  unsigned value (0..255)
   *
   * @throws IllegalArgumentException  if {@code value} is not in the range 0..255
   * @throws IOException               if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  @SuppressWarnings("ConstantValue")
  public void writeSmallVar(@Range(from = 0, to = 255) int value) throws IOException
  {
    if (value < 0 || value > 255)
      throw new IllegalArgumentException("value " + value + " is not in range 0..255");

    if (value <= 7)
      writeSmall(value, 4);  // 0vvv
    else if (value <= 15)
      writeSmall(0b10_000 | (value - 8), 5);  // 10vvv (vvv + 8)
    else
      writeLarge(0b11_00000000 | value, 10);  // 11vvvvvvvv
  }


  /**
   * Writes a small unsigned value using exactly the specified number of bits (1..8).
   *
   * @param value     unsigned value (0..255)
   * @param bitWidth  number of bits to write (1..8)
   *
   * @throws IllegalArgumentException  if {@code bitWidth} is not in the range 1..8, or if the value is negative or does
   *                                   not fit in the given bit width
   * @throws IOException               if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  @SuppressWarnings("ConstantValue")
  public void writeSmall(@Range(from = 0, to = 255) int value,
                         @Range(from = 1, to = 8) int bitWidth) throws IOException
  {
    if (bitWidth < 1 || bitWidth > 8)
      throw new IllegalArgumentException("Invalid bitWidth: " + bitWidth);
    //noinspection ConstantValue
    if (value < 0 || value >= (1 << bitWidth))
      throw new IllegalArgumentException("value " + value + " does not fit in " + bitWidth + " bits");

    var bitsRemaining = bit + 1 - bitWidth;

    if (bitsRemaining > 0)
    {
      b |= (byte)((value & ((1 << bitWidth) - 1)) << bitsRemaining);
      bit -= bitWidth;
    }
    else if (bitsRemaining == 0)
    {
      stream.write(b | (value & ((1 << bitWidth) - 1)));
      b = 0;
      bit = 7;
    }
    else  // bitsRemaining < 0
    {
      stream.write(b | (byte)(value >>> -bitsRemaining) & ((1 << (bit + 1)) - 1));
      b = (byte)((value << (8 + bitsRemaining)) & 0xff);
      bit = 7 + bitsRemaining;
    }
  }


  /**
   * Writes an unsigned value using exactly the specified number of bits (9..64).
   *
   * @param value     the unsigned value to write. For a {@code bitWidth} of 64, all values are accepted and written as
   *                  is; otherwise the value must be in the range 0..2<sup>bitWidth</sup>-1
   * @param bitWidth  number of bits to write (9..64)
   *
   * @throws IllegalArgumentException  if {@code bitWidth} is not in the range 9..64, or if the value does not fit in
   *                                   the given bit width
   * @throws IOException               if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  @SuppressWarnings("ConstantValue")
  public void writeLarge(long value, @Range(from = 9, to = 64) int bitWidth) throws IOException
  {
    if (bitWidth < 9 || bitWidth > 64)
      throw new IllegalArgumentException("Invalid bitWidth: " + bitWidth);
    if (bitWidth < 64 && (value >>> bitWidth) != 0)
      throw new IllegalArgumentException("value " + value + " does not fit in " + bitWidth + " bits");

    if (bit < 7)
    {
      var bitsLeft = bit + 1 - bitWidth;

      b |= (byte)((byte)(value >>> -bitsLeft) & ((1 << (bit + 1)) - 1));
      stream.write(b);

      bitWidth -= bit + 1;
      b = 0;
      bit = 7;
    }

    while(bitWidth >= 8)
      stream.write((byte)(value >>> (bitWidth -= 8)));

    if (bitWidth > 0)
    {
      b = (byte)((value << (8 - bitWidth)) & 0xff);
      bit = 7 - bitWidth;
    }
  }


  /**
   * Writes any partially written byte to the underlying stream, padding the remaining bits with zeros to reach byte
   * alignment.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  protected void forceByteAlignment() throws IOException
  {
    if (bit != 7)
    {
      stream.write(b);
      bit = 7;
      b = 0;
    }
  }


  /**
   * {@inheritDoc}
   * <p>
   * All complete bytes written so far are passed to the underlying output stream. If the payload is compressed, the
   * compressor is flushed with {@link java.util.zip.Deflater#SYNC_FLUSH SYNC_FLUSH}, so that the receiver can
   * decompress all data written so far. Each such flush slightly reduces the compression ratio.
   * <p>
   * The bits of a partially written byte stay buffered until further writes complete the byte or the stream is
   * {@linkplain #close() closed}.
   *
   * @throws IOException  if an I/O error occurs
   *
   * @since 0.3.2
   */
  @Override
  @Contract(mutates = "this,io")
  public void flush() throws IOException {
    stream.flush();
  }


  /**
   * {@inheritDoc}
   * <p>
   * The underlying output stream is closed as well. Any buffered bits are written first (padded with zeros to a full
   * byte), and a compressed payload is finished. The underlying output stream is flushed before it is closed.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Override
  public void close() throws IOException
  {
    forceByteAlignment();

    // finish first, so the sync flush doesn't add an empty block before the GZIP trailer
    if (stream instanceof DeflaterOutputStream deflaterStream)
      deflaterStream.finish();

    stream.flush();
    stream.close();
  }
}
