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

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;


/**
 * Per-class cache of enum constants.
 * <p>
 * {@link Class#getEnumConstants()} returns a new copy of the constants array on every call. This cache computes the
 * array once per enum class, so reading and writing enum values does not allocate. The cached arrays are shared and
 * must never be modified or exposed.
 *
 * @author Jeroen Gremmen
 * @since 0.3.2
 */
final class EnumConstants
{
  /** Constants of each enum class, computed on first access. */
  private static final ClassValue<Enum<?>[]> CONSTANTS = new ClassValue<>() {
    @Override
    protected Enum<?>[] computeValue(@NotNull Class<?> type) {
      return (Enum<?>[])type.getEnumConstants();
    }
  };


  private EnumConstants() {
  }


  /**
   * Returns the cached constants of the given enum class.
   *
   * @param enumType  the enum class, not {@code null}
   * @param <T>       the enum type
   *
   * @return  the shared constants array, which must not be modified, never {@code null}
   */
  @Contract(pure = true)
  @SuppressWarnings("unchecked")
  static <T extends Enum<T>> @NotNull T[] of(@NotNull Class<T> enumType) {
    return (T[])CONSTANTS.get(enumType);
  }


  /**
   * Returns the number of constants of the given enum class.
   *
   * @param enumType  the enum class, not {@code null}
   *
   * @return  the number of enum constants
   */
  @Contract(pure = true)
  static int count(@NotNull Class<? extends Enum<?>> enumType) {
    return CONSTANTS.get(enumType).length;
  }
}
