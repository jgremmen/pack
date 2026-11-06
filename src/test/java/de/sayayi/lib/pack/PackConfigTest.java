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
package de.sayayi.lib.pack;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;


/**
 * Tests {@link PackConfig} and its {@link PackConfig.Builder builder}.
 * <p>
 * Verifies that the magic bytes of a configuration cannot be changed afterwards by modifying the array passed to the
 * builder (before or after building) or to the constructor.
 *
 * @author Jeroen Gremmen
 * @since 0.3.2
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Pack configuration")
class PackConfigTest
{
  @Test
  @DisplayName("Magic array modified after build")
  void magicModifiedAfterBuild()
  {
    final var magic = "ABC".getBytes(US_ASCII);
    final var packConfig = new PackConfig.Builder().withMagic(magic).build();

    magic[0] = 'Z';

    assertArrayEquals("ABC".getBytes(US_ASCII), packConfig.getMagic());
  }


  @Test
  @DisplayName("Magic array modified before build")
  void magicModifiedBeforeBuild()
  {
    final var magic = "ABC".getBytes(US_ASCII);
    final var builder = new PackConfig.Builder().withMagic(magic);

    magic[0] = 'Z';

    assertArrayEquals("ABC".getBytes(US_ASCII), builder.build().getMagic());
  }


  @Test
  @DisplayName("Magic array passed to constructor")
  void magicPassedToConstructor()
  {
    final var magic = "ABC".getBytes(US_ASCII);
    final var packConfig = new PackConfig(magic, 0, 0, false) {};

    magic[0] = 'Z';

    assertArrayEquals("ABC".getBytes(US_ASCII), packConfig.getMagic());
  }
}
