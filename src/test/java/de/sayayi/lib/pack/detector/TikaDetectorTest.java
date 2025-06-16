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
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.junit.jupiter.api.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.apache.tika.mime.MediaType.OCTET_STREAM;
import static org.apache.tika.mime.MediaType.TEXT_PLAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
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

    final var mediaType = detector.detect(new ByteArrayInputStream(byteStream.toByteArray()), new Metadata());

    assertEquals(MediaType.parse("application/my-bitpack"), mediaType.getBaseType());
    assertEquals("85", mediaType.getParameters().get("version"));
    assertEquals("true", mediaType.getParameters().get("compress"));
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




  private static final class MyTikaDetector extends AbstractTikaDetector
  {
    public MyTikaDetector() {
      super(PACK_CONFIG, "application/my-bitpack");
    }
  }
}
