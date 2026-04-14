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
/**
 * Provides abstract base classes for detecting pack file content types.
 * <p>
 * This package contains detectors that identify pack files and determine their MIME type by reading and validating
 * the pack stream header. Two detection strategies are supported:
 * <ul>
 *   <li>
 *     {@link de.sayayi.lib.pack.detector.AbstractFileTypeDetector} &ndash; for the
 *     {@linkplain java.nio.file.spi.FileTypeDetector Java NIO file type detection SPI}
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.pack.detector.AbstractTikaDetector} &ndash; for the Apache Tika detection API
 *   </li>
 * </ul>
 */
package de.sayayi.lib.pack.detector;
