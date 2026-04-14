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
 * Provides classes for reading and writing data in a compact bit-level binary format.
 * <p>
 * The core classes in this package are:
 * <ul>
 *   <li>
 *     {@link de.sayayi.lib.pack.PackConfig} &ndash; defines the structure of a pack stream header,
 *     including magic bytes, version range, and compression support
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.pack.PackOutputStream} &ndash; writes data to a pack stream
 *   </li>
 *   <li>
 *     {@link de.sayayi.lib.pack.PackInputStream} &ndash; reads data from a pack stream
 *   </li>
 * </ul>
 */
package de.sayayi.lib.pack;
