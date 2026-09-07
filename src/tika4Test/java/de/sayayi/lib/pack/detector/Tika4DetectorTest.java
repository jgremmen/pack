/*
 * Copyright 2026 Jeroen Gremmen
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
import org.apache.tika.detect.Detector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.annotation.ElementType;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.apache.tika.mime.MediaType.OCTET_STREAM;
import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * Tests {@link AbstractTika4Detector}, compiled and executed against Apache Tika 4.
 *
 * @author Jeroen Gremmen
 * @since 0.3.1
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Tika 4 media type detector")
class Tika4DetectorTest
{
  private static final PackConfig PACK_CONFIG = new PackConfig.Builder()
      .withVersionRange(1, 100)
      .withCompressionSupport(true)
      .withMagic("MyTeSt\u0007**")
      .build();

  private Detector detector;


  @BeforeEach
  void init() {
    detector = new MyTika4Detector();
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

    final var mediaType =
        detector.detect(TikaInputStream.get(byteStream.toByteArray()), new Metadata(), new ParseContext());

    assertEquals(MediaType.parse("application/my-bitpack"), mediaType.getBaseType());
    assertEquals("85", mediaType.getParameters().get("version"));
    assertEquals("true", mediaType.getParameters().get("compress"));
  }


  @Test
  @DisplayName("Detect empty stream")
  void detectEmpty() throws IOException {
    assertEquals(OCTET_STREAM,
        detector.detect(TikaInputStream.get(new byte[0]), new Metadata(), new ParseContext()));
  }


  @Test
  @DisplayName("Detect incomplete stream")
  void detectIncomplete() throws IOException {
    assertEquals(OCTET_STREAM,
        detector.detect(TikaInputStream.get("MyTeSt".getBytes(US_ASCII)), new Metadata(), new ParseContext()));
  }




  private static final class MyTika4Detector extends AbstractTika4Detector
  {
    public MyTika4Detector() {
      super(PACK_CONFIG, "application/my-bitpack");
    }
  }
}
