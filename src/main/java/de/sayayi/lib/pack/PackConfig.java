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

import java.nio.charset.Charset;
import java.util.Arrays;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.Objects.requireNonNull;


/**
 * Configuration for reading and writing pack streams.
 * <p>
 * A pack configuration defines the structure of a pack stream header, including the magic bytes used to identify
 * the format, the supported version range, and whether compression is enabled.
 * <p>
 * Use the {@link Builder} to create instances of this class.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 *
 * @see PackInputStream
 * @see PackOutputStream
 */
public class PackConfig
{
  protected final byte[] magic;
  protected final int lowestVersionNumber;
  protected final int versionBits;
  protected final boolean compressionSupport;


  /**
   * Creates a new pack configuration.
   *
   * @param magic               magic bytes used to identify the pack format
   * @param lowestVersionNumber the lowest version number in the supported range
   * @param versionBits         number of bits used to encode the version number ({@code 0} if versioning is disabled)
   * @param compressionSupport  {@code true} if the pack format supports compression
   */
  protected PackConfig(byte @NotNull [] magic, int lowestVersionNumber,
                       @Range(from = 0, to = 31) int versionBits, boolean compressionSupport)
  {
    this.magic = requireNonNull(magic);
    this.lowestVersionNumber = lowestVersionNumber;
    this.versionBits = versionBits;
    this.compressionSupport = compressionSupport;
  }


  /**
   * Returns a copy of the magic bytes used to identify the pack format.
   *
   * @return  magic bytes, never {@code null}
   */
  @Contract(pure = true)
  public byte @NotNull [] getMagic() {
    return Arrays.copyOf(magic, magic.length);
  }


  /**
   * Returns the lowest version number in the supported range.
   *
   * @return  the lowest supported version number
   */
  @Contract(pure = true)
  public int getLowestVersionNumber() {
    return lowestVersionNumber;
  }


  /**
   * Returns the highest version number in the supported range.
   *
   * @return  the highest supported version number
   */
  @Contract(pure = true)
  public int getHighestVersionNumber() {
    return lowestVersionNumber + (int)((1L << versionBits) - 1);
  }


  /**
   * Returns the number of bits used to encode the version number in the pack stream header.
   *
   * @return  version bit width, or {@code 0} if versioning is disabled
   */
  @Contract(pure = true)
  public @Range(from = 0, to = 31) int getVersionBits() {
    return versionBits;
  }


  /**
   * Tells whether this pack format supports compression.
   *
   * @return  {@code true} if compression is supported, {@code false} otherwise
   */
  @Contract(pure = true)
  public boolean isCompressionSupport() {
    return compressionSupport;
  }




  /**
   * A builder for creating {@link PackConfig} instances.
   * <p>
   * The builder provides a fluent API to configure magic bytes, version range, and compression support. By default,
   * the configuration has no magic bytes, no versioning, and compression disabled.
   */
  public static class Builder
  {
    private byte[] magic = new byte[0];
    private int lowestVersionNumber = 0;
    private int versionBits = 0;
    private boolean compressionSupport = false;


    /**
     * Disables versioning for this pack configuration.
     *
     * @return  this builder
     */
    @Contract("-> this")
    public @NotNull Builder noVersion()
    {
      lowestVersionNumber = 0;
      versionBits = 0;

      return this;
    }


    /**
     * Sets the magic bytes used to identify the pack format.
     *
     * @param magic  magic bytes, not {@code null}
     *
     * @return  this builder
     */
    @Contract("_ -> this")
    public @NotNull Builder withMagic(byte @NotNull [] magic)
    {
      this.magic = requireNonNull(magic, "magic must not be null");
      return this;
    }


    /**
     * Sets the magic bytes from the given string, encoded as
     * {@link java.nio.charset.StandardCharsets#US_ASCII US-ASCII}.
     *
     * @param magic  magic string, not {@code null}
     *
     * @return  this builder
     */
    @Contract("_ -> this")
    public @NotNull Builder withMagic(@NotNull String magic) {
      return withMagic(magic.getBytes(US_ASCII));
    }


    /**
     * Sets the magic bytes from the given string, encoded using the specified charset.
     *
     * @param magic    magic string, not {@code null}
     * @param charset  charset to use for encoding, not {@code null}
     *
     * @return  this builder
     */
    @Contract("_, _ -> this")
    public @NotNull Builder withMagic(@NotNull String magic, @NotNull Charset charset) {
      return withMagic(magic.getBytes(charset));
    }


    /**
     * Enables compression support.
     *
     * @return  this builder
     */
    @Contract("-> this")
    public @NotNull Builder withCompressionSupport() {
      return withCompressionSupport(true);
    }


    /**
     * Enables or disables compression support.
     *
     * @param compressionSupport  {@code true} to enable compression support
     *
     * @return  this builder
     */
    @Contract("_ -> this")
    public @NotNull Builder withCompressionSupport(boolean compressionSupport)
    {
      this.compressionSupport = compressionSupport;
      return this;
    }


    /**
     * Sets the supported version range. The number of bits required to encode the version is automatically derived
     * from the range.
     *
     * @param lowestVersion   the lowest supported version number, must not be negative
     * @param highestVersion  the highest supported version number, must be &ge; {@code lowestVersion}
     *
     * @return  this builder
     *
     * @throws IllegalArgumentException  if {@code lowestVersion} is negative or greater than {@code highestVersion}
     */
    @Contract("_, _ -> this")
    public @NotNull Builder withVersionRange(int lowestVersion, int highestVersion)
    {
      if (lowestVersion < 0)
        throw new IllegalArgumentException("lowestVersion must not be negative");
      if (lowestVersion > highestVersion)
        throw new IllegalArgumentException("lowestVersion must not be larger than highestVersion");

      lowestVersionNumber = lowestVersion;

      var v = highestVersion - lowestVersion;
      v |= v >> 1;
      v |= v >> 2;
      v |= v >> 4;
      v |= v >> 8;
      v |= v >> 16;

      this.versionBits = Integer.bitCount(v);

      return this;
    }


    /**
     * Builds a new {@link PackConfig} from the current builder state.
     *
     * @return  a new pack configuration, never {@code null}
     */
    @Contract(value = "-> new", pure = true)
    public @NotNull PackConfig build() {
      return new PackConfig(magic, lowestVersionNumber, versionBits, compressionSupport);
    }
  }
}
