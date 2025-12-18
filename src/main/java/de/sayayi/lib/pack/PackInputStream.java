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
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
public class PackInputStream implements Closeable
{
  private @NotNull InputStream stream;
  private final boolean compressed;
  private final Integer version;
  private int bit = -1;
  private byte b;


  /**
   * @since 0.1.2
   */
  @Contract(mutates = "param1,io")
  public PackInputStream(@NotNull InputStream stream) throws IOException {
    this(new PackConfig.Builder().withCompressionSupport().build(), stream);
  }


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


  @Contract(pure = true)
  public boolean isCompressed() {
    return compressed;
  }


  @Contract(pure = true)
  public @NotNull OptionalInt getVersion() {
    return version == null ? OptionalInt.empty() : OptionalInt.of(version);
  }


  @Contract(mutates = "this,io")
  public boolean readBoolean() throws IOException
  {
    assertData();

    return (b & (1 << bit--)) != 0;
  }


  @Contract(mutates = "this,io")
  public void skipBoolean()  throws IOException
  {
    assertData();
    bit--;
  }


  @Contract(mutates = "this,io")
  public <T extends Enum<T>> @NotNull T readEnum(@NotNull Class<T> enumType,
                                                 @Range(from = 1, to = 16) int bitWidth) throws IOException
  {
    //noinspection ConstantValue
    if (bitWidth <= 0 || bitWidth > 16)
      throw new IllegalArgumentException("Invalid bitWidth: " + bitWidth);

    return enumType.getEnumConstants()[bitWidth <= 8 ? readSmall(bitWidth) : (int)readLarge(bitWidth)];
  }


  @Contract(mutates = "this,io")
  public <T extends Enum<T>> @NotNull T readEnum(@NotNull Class<T> enumType) throws IOException
  {
    final var enums = enumType.getEnumConstants();
    final var n = enums.length;
    final var bits = bitCount(n | (n >> 1) | (n >> 2) | (n >> 4) | (n >> 8));

    return enums[bits <= 8 ? readSmall(bits) : (int)readLarge(bits)];
  }


  @Contract(mutates = "this,io")
  public <T extends Enum<T>> void skipEnum(@NotNull Class<T> enumType) throws IOException
  {
    final var enums = enumType.getEnumConstants();
    final var n = enums.length;

    skip(bitCount(n | (n >> 1) | (n >> 2) | (n >> 4) | (n >> 8)));
  }


  /**
   * @return  unsigned value (0..65535)
   *
   * @throws IOException  if an I/O error occurs
   */
  @Contract(mutates = "this,io")
  public @Range(from = 0, to = 65535) int readUnsignedShort() throws IOException {
    return (int)readLarge(16);
  }


  @Contract(mutates = "this,io")
  public void skipUnsignedShort() throws IOException {
    skip(16);
  }


  @Contract(mutates = "this,io")
  public int readInt() throws IOException {
    return (int)readLarge(32);
  }


  @Contract(mutates = "this,io")
  public void skipInt() throws IOException {
    skip(32);
  }


  @Contract(mutates = "this,io")
  public long readLong() throws IOException {
    return readLarge(64);
  }


  @Contract(mutates = "this,io")
  public void skipLong() throws IOException {
    skip(64);
  }


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
   * Ranges: 0..7 (4 bit), 8..15 (5 bit), 16..255 (10 bit)
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


  @Contract(mutates = "this,io")
  public void skipSmallVar() throws IOException
  {
    final var v4 = readSmall(4);

    if ((v4 & 0b1000) != 0)
      skip((v4 & 0b0100) == 0 ? 1 : 6);
  }


  /**
   * @param bitWidth  bit width (1..8)
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
   * @param bitWidth  bit width (9..64)
   *
   * @return  long value
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


  @Contract(mutates = "this,io")
  protected void assertData() throws IOException
  {
    if (bit < 0)
    {
      b = read();
      bit = 7;
    }
  }


  @Contract(mutates = "this")
  protected void forceByteAlignment()
  {
    if (bit >= 0)
      bit = -1;
  }


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


  @Override
  public void close() throws IOException {
    stream.close();
  }


  @Contract(mutates = "io")
  protected byte read() throws IOException
  {
    final var c = stream.read();
    if (c < 0)
      throw new EOFException("unexpected end of pack stream");

    return (byte)c;
  }
}
