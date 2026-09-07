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
 *     {@link de.sayayi.lib.pack.detector.AbstractFileTypeDetector} - for the
 *     {@linkplain java.nio.file.spi.FileTypeDetector Java NIO file type detection SPI}
 *   </li>
 *   <li>
 *     {@code AbstractTika3Detector} - for the Apache Tika detection API, compiled against and intended to be used
 *     with Apache Tika 3
 *   </li>
 *   <li>
 *     {@code AbstractTika4Detector} - for the Apache Tika detection API, compiled against and intended to be used
 *     with Apache Tika 4
 *   </li>
 * </ul>
 * <p>
 * The Apache Tika detector base classes are not part of this module's main compilation; each is compiled separately
 * against its respective Tika version (Tika 3 or Tika 4) and packaged into this same package. Depending on which
 * version of Apache Tika is available on the classpath at compile and runtime, only the matching class can be
 * referenced and used.
 */
package de.sayayi.lib.pack.detector;
