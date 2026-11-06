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
package de.sayayi.lib.pack.detector;

import de.sayayi.lib.pack.PackConfig;
import de.sayayi.lib.pack.PackOutputStream;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;
import java.nio.file.Path;
import java.util.OptionalInt;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.nio.file.Files.newOutputStream;
import static org.apache.tika.mime.MediaType.OCTET_STREAM;
import static org.apache.tika.mime.MediaType.TEXT_PLAIN;
import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests {@link AbstractTika3Detector}, compiled and executed against Apache Tika 3.
 * <p>
 * Covers detection of pack streams and files with and without compression support, the annotated media type returned,
 * leaving the probed stream open, rejection of pack configurations without magic bytes, and the fallback for empty and
 * incomplete streams.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Tika media type detector")
class TikaDetectorTest
{
  private static final PackConfig PACK_CONFIG = new PackConfig.Builder()
      .withVersionRange(1, 100)
      .withCompressionSupport(true)
      .withMagic("MyTeSt\u0007**")
      .build();

  private Detector detector;


  @BeforeEach
  void init() throws IOException, TikaException {
    detector = new CompositeDetector(new MyTikaDetector(), new TikaConfig().getDetector());
  }


  @Test
  @DisplayName("Detect valid stream")
  void detectValid() throws IOException
  {
    final var byteStream = new ByteArrayOutputStream();

    try(var packStream = new PackOutputStream(PACK_CONFIG, 85, byteStream)) {
      packStream.writeSmall(5, 3);
      packStream.writeBoolean(true);
      packStream.writeEnum(ElementType.LOCAL_VARIABLE);
    }

    final var mediaType = detector
        .detect(new ByteArrayInputStream(byteStream.toByteArray()), new Metadata());

    assertEquals(MediaType.parse("application/my-bitpack"), mediaType.getBaseType());
    assertEquals("85", mediaType.getParameters().get("version"));
    assertEquals("true", mediaType.getParameters().get("compress"));
  }


  @Test
  @DisplayName("Detect valid file stream without closing it")
  void detectValidFile(@TempDir Path tempDir) throws IOException
  {
    final var packPath = tempDir.resolve("test.pack");

    try(var packStream = new PackOutputStream(PACK_CONFIG, 85, newOutputStream(packPath))) {
      packStream.writeInt(42);
    }

    try(var input = TikaInputStream.get(packPath)) {
      final var mediaType = detector.detect(input, new Metadata());

      assertEquals(MediaType.parse("application/my-bitpack"), mediaType.getBaseType());
      assertEquals("85", mediaType.getParameters().get("version"));
      assertEquals("true", mediaType.getParameters().get("compress"));

      // stream must still be open and reset to its start
      assertArrayEquals(PACK_CONFIG.getMagic(), input.readNBytes(PACK_CONFIG.getMagic().length));
    }
  }


  @Test
  @DisplayName("Detect compressed stream within mark limit")
  void detectWithinMarkLimit() throws IOException
  {
    // version 85 of 1..100 is encoded as a 10-bit SmallVar
    assertDetectedWithinMarkLimit(PACK_CONFIG, 85);

    // version range requiring 31 bits
    assertDetectedWithinMarkLimit(new PackConfig
        .Builder()
        .withVersionRange(0, 1 << 30)
        .withCompressionSupport(true)
        .withMagic("MyTeSt\u0007**")
        .build(), 1 << 30);
  }


  private static void assertDetectedWithinMarkLimit(PackConfig packConfig, int version) throws IOException
  {
    final var byteStream = new ByteArrayOutputStream();

    try(var packStream = new PackOutputStream(packConfig, version, true, byteStream)) {
      packStream.writeInt(42);
    }

    final var input = new StrictMarkInputStream(byteStream.toByteArray());
    final var mediaType = new AbstractTika3Detector(packConfig, "application/my-bitpack") {}
        .detect(input, new Metadata());

    assertEquals(MediaType.parse("application/my-bitpack"), mediaType.getBaseType());
    assertEquals(String.valueOf(version), mediaType.getParameters().get("version"));
    assertEquals("true", mediaType.getParameters().get("compress"));
  }


  @Test
  @DisplayName("Detect without compression support")
  void detectWithoutCompressionSupport() throws IOException
  {
    final var packConfig = new PackConfig
        .Builder()
        .withMagic("NoCoMpReSs")
        .withVersionRange(1, 100)
        .build();
    final var byteStream = new ByteArrayOutputStream();

    new PackOutputStream(packConfig, 12, byteStream).close();

    final var mediaType = new AbstractTika3Detector(packConfig, "application/my-bitpack") {}
        .detect(new ByteArrayInputStream(byteStream.toByteArray()), new Metadata());

    assertEquals(MediaType.parse("application/my-bitpack"), mediaType.getBaseType());
    assertEquals("12", mediaType.getParameters().get("version"));
    assertFalse(mediaType.getParameters().containsKey("compress"));
  }


  @Test
  @DisplayName("Reject configuration without magic bytes")
  void rejectConfigWithoutMagic()
  {
    final var packConfig = new PackConfig
        .Builder()
        .withVersionRange(1, 100)
        .withCompressionSupport()
        .build();

    assertThrows(
        IllegalArgumentException.class,
        () -> new AbstractTika3Detector(packConfig, "application/my-bitpack") {});
  }


  @Test
  @DisplayName("Propagate runtime exceptions")
  void propagateRuntimeException() throws IOException
  {
    final var byteStream = new ByteArrayOutputStream();

    new PackOutputStream(PACK_CONFIG, 85, byteStream).close();

    final var detector = new AbstractTika3Detector(PACK_CONFIG, "application/my-bitpack") {
      @Override
      protected @NotNull MediaType buildAnnotatedMimeType(@NotNull OptionalInt version, Boolean compressed) {
        throw new IllegalStateException("bug");
      }
    };
    final var input = new ByteArrayInputStream(byteStream.toByteArray());

    assertEquals("bug", assertThrows(IllegalStateException.class, () -> detector.detect(input, new Metadata()))
        .getMessage());

    // stream must have been reset to its start
    assertArrayEquals(PACK_CONFIG.getMagic(), input.readNBytes(PACK_CONFIG.getMagic().length));
  }


  @Test
  @DisplayName("Detect empty stream")
  void detectEmpty() throws IOException {
    assertEquals(OCTET_STREAM, detector.detect(new ByteArrayInputStream(new byte[0]), new Metadata()));
  }


  @Test
  @DisplayName("Detect incomplete stream")
  void detectIncomplete() throws IOException {
    assertEquals(TEXT_PLAIN, detector.detect(new ByteArrayInputStream("MyTeSt".getBytes(US_ASCII)), new Metadata()));
  }




  /**
   * Input stream that invalidates its mark once more than {@code readLimit} bytes have been read, like a
   * {@link java.io.BufferedInputStream} that has to refill its buffer.
   */
  private static final class StrictMarkInputStream extends FilterInputStream
  {
    private long pos;
    private long markPos = -1;
    private int markLimit;


    private StrictMarkInputStream(byte[] data) {
      super(new ByteArrayInputStream(data));
    }


    @Override
    public int read() throws IOException
    {
      final var b = super.read();
      if (b >= 0)
        pos++;

      return b;
    }


    @Override
    public int read(byte @NotNull [] b, int off, int len) throws IOException
    {
      final var n = super.read(b, off, len);
      if (n > 0)
        pos += n;

      return n;
    }


    @Override
    public long skip(long n) throws IOException
    {
      final var skipped = super.skip(n);
      pos += skipped;

      return skipped;
    }


    @Override
    public synchronized void mark(int readLimit)
    {
      super.mark(readLimit);
      markPos = pos;
      markLimit = readLimit;
    }


    @Override
    public synchronized void reset() throws IOException
    {
      if (markPos < 0 || pos - markPos > markLimit)
        throw new IOException("mark invalidated: read " + (pos - markPos) + " bytes, limit " + markLimit);

      super.reset();
      pos = markPos;
    }
  }




  private static final class MyTikaDetector extends AbstractTika3Detector
  {
    public MyTikaDetector() {
      super(PACK_CONFIG, "application/my-bitpack");
    }
  }
}
