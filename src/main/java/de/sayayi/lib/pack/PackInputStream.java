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

import java.io.*;
import java.util.Arrays;
import java.util.OptionalInt;
import java.util.zip.GZIPInputStream;

import static java.lang.Integer.MAX_VALUE;
import static java.lang.Integer.bitCount;


/**
 * Input stream for reading data in the pack binary format.
 * <p>
 * A pack input stream reads and validates the header defined by the provided {@link PackConfig} (magic bytes,
 * compression flag, and version number) and then exposes methods for reading various data types at the bit level.
 * <p>
 * When compression is detected in the header, the payload following the header is transparently decompressed using a
 * {@link GZIPInputStream GZIP} stream.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see PackOutputStream
 * @see PackConfig
 */
public class PackInputStream implements Closeable
{
  private @NotNull InputStream stream;
  private final boolean compressed;
  private final Integer version;
  private int bit = -1;
  private byte b;


  /**
   * Creates a new pack input stream with default configuration (compression support enabled, no magic bytes,
   * no versioning).
   *
   * @param stream  the underlying input stream to read from, not {@code null}
   *
   * @throws IOException  if an I/O error occurs while reading the header
   *
   * @since 0.1.2
   */
  @Contract(mutates = "param1,io")
  public PackInputStream(@NotNull InputStream stream) throws IOException {
    this(new PackConfig.Builder().withCompressionSupport().build(), stream);
  }


  /**
   * Creates a new pack input stream with the given configuration. The header is read and validated immediately.
   *
   * @param packConfig  pack configuration defining the expected header format, not {@code null}
   * @param stream      the underlying input stream to read from, not {@code null}
   *
   * @throws IOException  if an I/O error occurs while reading the header or if the magic bytes do not match
   */
  @Contract(mutates = "param2,io")
  public PackInputStream(@NotNull PackConfig packConfig, @NotNull InputStream stream) throws IOException
  {
    this.stream = stream;

    final var magic = packConfig.getMagic();
    final var magicLength = magic.length;

    if (magicLength != 0)
    {
      final var header = new byte[magicLength];
      if (stream.readNBytes(header, 0, magicLength) != magicLength || !Arrays.equals(header, magic))
        throw new IOException("pack stream has wrong header magic");
    }

    compressed = packConfig.isCompressionSupport() && readBoolean();

    final var versionBits = packConfig.getVersionBits();
    if (versionBits != 0)
    {
      if (versionBits <= 8)
        version = readSmallVar() + packConfig.getLowestVersionNumber();
      else
        version = (int)(readLarge(versionBits) + packConfig.getLowestVersionNumber());
    }
    else
      version = null;

    if (compressed)
    {
      forceByteAlignment();
      this.stream = new GZIPInputStream(stream);
    }
  }


  /**
   * Tells whether the pack stream payload is compressed.
   *
   * @return  {@code true} if the payload is GZIP-compressed, {@code false} otherwise
   */
  @Contract(pure = true)
  public boolean isCompressed() {
    return compressed;
  }


  /**
   * Returns the version number read from the pack stream header.
   *
   * @return  the version number, or empty if versioning is not configured
   */
  @Contract(pure = true)
  public @NotNull OptionalInt getVersion() {
    return version == null ? OptionalInt.empty() : OptionalInt.of(version);
  }


  /**
   * Reads a single boolean value from the stream.
   *
   * @return  the boolean value read
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public boolean readBoolean() throws IOException
  {
    assertData();

    return (b & (1 << bit--)) != 0;
  }


  /**
   * Skips a single boolean value in the stream.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void skipBoolean()  throws IOException
  {
    assertData();
    bit--;
  }


  /**
   * Reads an enumerated value from the stream using the specified bit width.
   *
   * @param enumType  the enum class to read, not {@code null}
   * @param bitWidth  number of bits used for the enumerated value (1..16)
   * @param <T>       the enum type
   *
   * @return  the enum constant read, never {@code null}
   *
   * @throws IllegalArgumentException  if {@code bitWidth} is not in the range 1..16
   * @throws IOException               if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public <T extends Enum<T>> @NotNull T readEnum(@NotNull Class<T> enumType,
                                                 @Range(from = 1, to = 16) int bitWidth) throws IOException
  {
    //noinspection ConstantValue
    if (bitWidth <= 0 || bitWidth > 16)
      throw new IllegalArgumentException("Invalid bitWidth: " + bitWidth);

    return enumType.getEnumConstants()[bitWidth <= 8 ? readSmall(bitWidth) : (int)readLarge(bitWidth)];
  }


  /**
   * Reads an enumerated value from the stream. The bit width is automatically derived from the number of constants
   * in the enum type.
   *
   * @param enumType  the enum class to read, not {@code null}
   * @param <T>       the enum type
   *
   * @return  the enum constant read, never {@code null}
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public <T extends Enum<T>> @NotNull T readEnum(@NotNull Class<T> enumType) throws IOException
  {
    final var enums = enumType.getEnumConstants();
    final var n = enums.length;
    final var bits = bitCount(n | (n >> 1) | (n >> 2) | (n >> 4) | (n >> 8));

    return enums[bits <= 8 ? readSmall(bits) : (int)readLarge(bits)];
  }


  /**
   * Skips an enumerated value in the stream. The bit width is automatically derived from the number of constants in
   * the enum type.
   *
   * @param enumType  the enum class to skip, not {@code null}
   * @param <T>       the enum type
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public <T extends Enum<T>> void skipEnum(@NotNull Class<T> enumType) throws IOException
  {
    final var enums = enumType.getEnumConstants();
    final var n = enums.length;

    skip(bitCount(n | (n >> 1) | (n >> 2) | (n >> 4) | (n >> 8)));
  }


  /**
   * Reads an unsigned 16-bit value from the stream.
   *
   * @return  unsigned value (0..65535)
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public @Range(from = 0, to = 65535) int readUnsignedShort() throws IOException {
    return (int)readLarge(16);
  }


  /**
   * Skips an unsigned 16-bit value in the stream.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void skipUnsignedShort() throws IOException {
    skip(16);
  }


  /**
   * Reads a 32-bit integer value from the stream.
   *
   * @return  the integer value read
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public int readInt() throws IOException {
    return (int)readLarge(32);
  }


  /**
   * Skips a 32-bit integer value in the stream.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void skipInt() throws IOException {
    skip(32);
  }


  /**
   * Reads a 64-bit long value from the stream.
   *
   * @return  the long value read
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public long readLong() throws IOException {
    return readLarge(64);
  }


  /**
   * Skips a 64-bit long value in the stream.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void skipLong() throws IOException {
    skip(64);
  }


  /**
   * Reads a string value from the stream using compact modified UTF-8 encoding.
   *
   * @return  the string read, or {@code null}
   *
   * @throws UTFDataFormatException  if the UTF-8 data is malformed
   * @throws IOException             if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public String readString() throws IOException
  {
    var utfBytesRemaining = 0;

    switch(readSmall(2))
    {
      case 0b00:
        return null;

      case 0b01:
        if ((utfBytesRemaining = readSmall(4)) == 0)
          return "";
        break;

      case 0b10:
        utfBytesRemaining = readSmall(8);
        break;

      case 0b11:
        utfBytesRemaining = (int)readLarge(16);
        break;
    }

    forceByteAlignment();

    final var chars = new char[utfBytesRemaining];  // safe size (probably too large)
    var charIdx = 0;

    for(int b1, b2, b3; utfBytesRemaining > 0;)
    {
      if (((b1 = read()) & 0b1000_0000) == 0b0000_0000)  // 0xxx xxxx
      {
        utfBytesRemaining--;
        chars[charIdx++] = (char)b1;
      }
      else if ((b1 & 0b1110_0000) == 0b1100_0000)  // 110x xxxx | 10xx xxxx
      {
        utfBytesRemaining -= 2;
        b2 = read();

        if ((b2 & 0b1100_0000) != 0b1000_0000)
          throw new UTFDataFormatException();

        chars[charIdx++] = (char)(((b1 & 0b0001_1111) << 6) | (b2 & 0b0011_1111));
      }
      else if ((b1 & 0b11110000) == 0b11100000)  // 1110 xxxx | 10xx xxxx | 10xx xxxx
      {
        utfBytesRemaining -= 3;
        b2 = read();
        b3 = read();

        if ((b2 & 0b1100_0000) != 0b1000_0000 ||
            (b3 & 0b1100_0000) != 0b1000_0000)
          throw new UTFDataFormatException();

        chars[charIdx++] = (char)(((b1 & 0b0000_1111) << 12) | ((b2 & 0b0011_1111) << 6) | (b3 & 0b0011_1111));
      }
      else  // 10xx xxxx, 1111 xxxx
        throw new UTFDataFormatException();
    }

    return new String(chars, 0, charIdx);
  }


  /**
   * Skips a string value in the stream.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void skipString() throws IOException
  {
    var utfLength = 0;

    switch(readSmall(2))
    {
      case 0b00:
        return;

      case 0b01:
        if ((utfLength = readSmall(4)) == 0)
          return;
        break;

      case 0b10:
        utfLength = readSmall(8);
        break;

      case 0b11:
        utfLength = (int)readLarge(16);
        break;
    }

    forceByteAlignment();

    while(utfLength-- > 0)
      read();
  }


  /**
   * Reads a small unsigned value (0..255) using a variable-width encoding that favors smaller values.
   *
   * @return  value in range 0..255
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public @Range(from = 0, to = 255) int readSmallVar() throws IOException
  {
    final var v4 = readSmall(4);

    if ((v4 & 0b1000) == 0)  // 0vvv
      return v4;
    else if ((v4 & 0b0100) == 0)  // 10vv_v (-> 1vvv)
      return ((v4 - 0b0100) << 1) | (readBoolean() ? 1 : 0);
    else  // 11vv_vvvvvv
      return ((v4 & 0b0011) << 6) | readSmall(6);
  }


  /**
   * Skips a variable-width encoded small value in the stream.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void skipSmallVar() throws IOException
  {
    final var v4 = readSmall(4);

    if ((v4 & 0b1000) != 0)
      skip((v4 & 0b0100) == 0 ? 1 : 6);
  }


  /**
   * Reads a small unsigned value using exactly the specified number of bits (1..8).
   *
   * @param bitWidth  number of bits to read (1..8)
   *
   * @return  value in range 0..255
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public @Range(from = 0, to = 255) int readSmall(@Range(from = 1, to = 8) int bitWidth) throws IOException
  {
    assertData();

    final var bitsRemaining = bit + 1 - bitWidth;

    if (bitsRemaining > 0)
    {
      bit = bitsRemaining - 1;
      return (b >> bitsRemaining) & ((1 << bitWidth) - 1);
    }
    else if (bitsRemaining == 0)
    {
      bit = -1;
      return b & ((1 << bitWidth) - 1);
    }
    else  // bitsRemaining < 0
    {
      var value = (b & ((1 << (bit + 1)) - 1)) << -bitsRemaining;

      bit = -1;
      assertData();

      value |= (b >> (8 + bitsRemaining)) & ((1 << -bitsRemaining) - 1);
      bit = 7 + bitsRemaining;
      return value;
    }
  }


  /**
   * Reads a value using exactly the specified number of bits (9..64).
   *
   * @param bitWidth  number of bits to read (9..64)
   *
   * @return  the value read
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public long readLarge(@Range(from = 9, to = 64) int bitWidth) throws IOException
  {
    assertData();

    long value = b & ((1L << (bit + 1)) - 1);

    for(bitWidth -= bit + 1; bitWidth >= 8; bitWidth -= 8)
    {
      var c = stream.read();
      if (c < 0)
        throw new EOFException();

      value = (value << 8) | c;
    }

    bit = -1;

    if (bitWidth > 0)
    {
      assertData();

      int c = (b >> (8 - bitWidth)) & ((1 << bitWidth) - 1);
      value = (value << bitWidth) | c;
      bit -= bitWidth;
    }

    return value;
  }


  /**
   * Ensures that a byte of data is available for bit-level reading. If the current byte has been fully consumed,
   * the next byte is read from the underlying stream.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  protected void assertData() throws IOException
  {
    if (bit < 0)
    {
      b = read();
      bit = 7;
    }
  }


  /**
   * Discards any remaining bits in the current byte to align the read position to the next byte boundary.
   */
  @Contract(mutates = "this")
  protected void forceByteAlignment()
  {
    if (bit >= 0)
      bit = -1;
  }


  /**
   * Skips the specified number of bits in the stream.
   *
   * @param bitWidth  number of bits to skip
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public void skip(@Range(from = 0, to = MAX_VALUE) int bitWidth) throws IOException
  {
    while(bitWidth > 0)
    {
      assertData();

      if (bitWidth <= (bit + 1))
      {
        bit -= bitWidth;
        break;
      }
      else
      {
        bitWidth -= bit + 1;
        bit = -1;
      }
    }
  }


  /**
   * Closes the underlying input stream.
   *
   * @throws IOException  if an I/O error occurs
   */
  @Override
  public void close() throws IOException {
    stream.close();
  }


  /**
   * Reads a single byte from the underlying stream.
   *
   * @return  the byte read
   *
   * @throws EOFException  if the end of the stream is reached unexpectedly
   * @throws IOException   if an I/O error occurs
   */
  @Contract(mutates = "io")
  protected byte read() throws IOException
  {
    final var c = stream.read();
    if (c < 0)
      throw new EOFException("unexpected end of pack stream");

    return (byte)c;
  }
}
