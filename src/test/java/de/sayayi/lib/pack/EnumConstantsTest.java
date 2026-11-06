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

import java.lang.annotation.ElementType;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Tests the per-class enum constants cache {@link EnumConstants}.
 *
 * @author Jeroen Gremmen
 * @since 0.3.2
 */
@TestMethodOrder(MethodOrderer.DisplayName.class)
@DisplayName("Enum constants cache")
class EnumConstantsTest
{
  @Test
  @DisplayName("Return the constants of an enum class")
  void constants()
  {
    assertArrayEquals(ElementType.values(), EnumConstants.of(ElementType.class));
    assertEquals(ElementType.values().length, EnumConstants.count(ElementType.class));
  }


  @Test
  @DisplayName("Return the same array on every call")
  @SuppressWarnings("EqualsWithItself")
  void cached() {
    assertSame(EnumConstants.of(ElementType.class), EnumConstants.of(ElementType.class));
  }
}
