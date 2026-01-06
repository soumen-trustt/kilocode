// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package ai.kilocode.jetbrains.filesystem

/**
 * Draft path protocol constant.
 * Use this constant everywhere instead of hardcoding "draft://"
 */
const val DRAFT_PROTOCOL = "draft://"

/**
 * Check if a path is a draft path (starts with draft://)
 * @param path The path to check
 * @return true if the path is a draft path
 */
fun isDraftPath(path: String): Boolean {
    return path.startsWith(DRAFT_PROTOCOL)
}

/**
 * Extract filename from a draft:// path.
 * Removes the protocol and any leading slashes.
 *
 * @param draftPath The draft path (e.g., "draft://filename.md" or "draft:///filename.md")
 * @return The filename without protocol or leading slashes (e.g., "filename.md")
 * @throws IllegalArgumentException if the path is not a valid draft path
 */
fun draftPathToFilename(draftPath: String): String {
    if (!draftPath.startsWith(DRAFT_PROTOCOL)) {
        throw IllegalArgumentException("Invalid draft path: $draftPath")
    }
    val filename = draftPath.substring(DRAFT_PROTOCOL.length)
    // Remove any leading slashes
    return if (filename.startsWith("/")) filename.substring(1) else filename
}

/**
 * Convert a filename to a draft:// path.
 * Removes any leading slashes from the filename before adding the protocol.
 *
 * @param filename The filename (e.g., "filename.md" or "/filename.md")
 * @return The draft path (e.g., "draft://filename.md")
 */
fun filenameToDraftPath(filename: String): String {
    // Remove any leading slashes from filename
    val cleanFilename = if (filename.startsWith("/")) filename.substring(1) else filename
    return "$DRAFT_PROTOCOL$cleanFilename"
}



