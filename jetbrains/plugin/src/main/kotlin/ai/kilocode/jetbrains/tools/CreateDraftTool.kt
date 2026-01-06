// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package ai.kilocode.jetbrains.tools

import ai.kilocode.jetbrains.filesystem.DraftFileSystemProvider
import com.intellij.openapi.diagnostic.Logger

/**
 * Tool handler for create_draft tool.
 * Creates ephemeral in-memory draft documents that appear as editor tabs.
 */
class CreateDraftTool(private val project: com.intellij.openapi.project.Project) {
    private val logger = Logger.getInstance(CreateDraftTool::class.java)

    /**
     * Handle create_draft tool invocation.
     * @param params Tool parameters containing title and content
     * @return Result map with success message and draft path
     */
    fun handle(params: Map<String, Any?>): Map<String, Any?> {
        try {
            // Extract and validate parameters
            val title = params["title"] as? String
            val content = params["content"] as? String

            if (title.isNullOrBlank()) {
                return mapOf(
                    "result" to "Error: 'title' parameter is required",
                    "error" to "Missing required parameter: title",
                )
            }

            if (content == null) {
                return mapOf(
                    "result" to "Error: 'content' parameter is required",
                    "error" to "Missing required parameter: content",
                )
            }

            // Create draft document
            val draftProvider = DraftFileSystemProvider.getInstance()
            val draftPath = draftProvider.createAndOpen(title, content, project)

            logger.info("Created draft document: $draftPath")

            // Return success message with instructions
            val message = """Created draft document "$title". The document has been opened in an editor tab.

You can now:
- Read it using: read_file with path "$draftPath"
- Update it using: write_to_file with path "$draftPath"

The document will be discarded when the editor session ends."""

            return mapOf(
                "result" to message,
                "id" to "create_draft",
                "draftPath" to draftPath,
            )
        } catch (e: Exception) {
            logger.error("Error creating draft document", e)
            return mapOf(
                "result" to "Error creating draft document: ${e.message}",
                "error" to e.message ?: "Unknown error",
            )
        }
    }
}



