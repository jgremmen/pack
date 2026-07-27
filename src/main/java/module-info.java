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

/**
 * Compact bit-level binary stream library for Java.
 * <p>
 * This module provides classes for reading and writing data in a compact binary format where values are packed using
 * the smallest possible number of bits. It supports configurable stream headers with magic bytes, version ranges, and
 * optional GZIP compression.
 * <p>
 * The main API consists of:
 * <ul>
 *   <li>
 *     {@link de.sayayi.lib.pack.PackConfig} - configures the binary stream format (magic bytes, versioning,
 *     compression)
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.pack.PackOutputStream} - writes booleans, enums, integers, longs, and strings at the bit
 *     level
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.pack.PackInputStream} - reads data back from a pack stream
 *   </li>
 * </ul>
 * <p>
 * The {@link de.sayayi.lib.pack.detector} package provides abstract base classes for detecting pack file content types
 * via the Java NIO {@link java.nio.file.spi.FileTypeDetector} SPI and the Apache Tika detection API.
 *
 * @author Jeroen Gremmen
 * @since 0.1.0
 */
module de.sayayi.lib.pack
{
  requires static org.apache.tika.core;
  requires static org.jetbrains.annotations;

  exports de.sayayi.lib.pack;
  exports de.sayayi.lib.pack.detector;
}