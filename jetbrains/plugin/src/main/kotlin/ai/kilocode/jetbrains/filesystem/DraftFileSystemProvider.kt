// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package ai.kilocode.jetbrains.filesystem

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

/**
 * File system provider for draft:// documents.
 * Stores documents in-memory and makes them available as editor tabs.
 * 
 * Note: This is an application-level singleton. Draft documents are shared across
 * all open projects in the IDE. This is intentional as drafts are ephemeral and
 * typically only one project is actively using the AI assistant at a time.
 * If project isolation is needed in the future, this can be refactored to use
 * a project-level service via @Service(Service.Level.PROJECT).
 */
class DraftFileSystemProvider private constructor() {
    private val logger = Logger.getInstance(DraftFileSystemProvider::class.java)
    
    // Store documents in-memory keyed by filename (without leading slash)
    private val documents = ConcurrentHashMap<String, ByteArray>()
    
    // Store virtual files for each draft document
    private val virtualFiles = ConcurrentHashMap<String, LightVirtualFile>()

    companion object {
        @Volatile
        private var instance: DraftFileSystemProvider? = null

        /**
         * Get the singleton draft file system provider instance.
         */
        fun getInstance(): DraftFileSystemProvider {
            return instance ?: synchronized(this) {
                instance ?: DraftFileSystemProvider().also { instance = it }
            }
        }
    }

    /**
     * Create a new draft document and open it in the editor.
     * @param name The name/title of the document (will be used as filename)
     * @param content Initial content of the document
     * @param project The IntelliJ project (optional, uses first open project if not provided)
     * @return The draft:// URI path (e.g., "draft://filename.md")
     */
    fun createAndOpen(name: String, content: String, project: Project? = null): String {
        // Ensure name has .md extension
        val filename = if (name.endsWith(".md")) name else "$name.md"

        // Store the document internally without leading slash
        val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
        documents[filename] = contentBytes

        // Create or update LightVirtualFile
        val virtualFile = virtualFiles.getOrPut(filename) {
            LightVirtualFile(filename, content)
        }
        // Update content if file already exists
        virtualFile.setContent(null, content, false)

        // Get the project to use - either the provided one or the first open project
        val targetProject = project ?: getFirstOpenProject()
        if (targetProject != null) {
            // Open the document in the editor
            ApplicationManager.getApplication().invokeAndWait {
                FileEditorManager.getInstance(targetProject).openFile(virtualFile, true)
            }
        } else {
            logger.warn("No open project found, cannot open draft document in editor: $filename")
        }

        logger.info("Created and opened draft document: $filename")

        // Return the draft:// path for use in tools (double slash, no slash in filename)
        return filenameToDraftPath(filename)
    }

    /**
     * Get the first open project in the IDE.
     * This is used as a fallback when no project is explicitly provided.
     */
    private fun getFirstOpenProject(): Project? {
        val projectManager = com.intellij.openapi.project.ProjectManager.getInstance()
        return projectManager.openProjects.firstOrNull()
    }

    /**
     * Read a draft document as a string.
     * @param path The draft:// path (e.g., "draft://foo.md")
     * @return The document content, or null if not found
     */
    fun readFileAsString(path: String): String? {
        // Extract filename from draft:// path (removes protocol and any slashes)
        val filename = draftPathToFilename(path)

        val content = documents[filename] ?: return null

        return String(content, StandardCharsets.UTF_8)
    }

    /**
     * Write content to a draft document.
     * @param path The draft:// path (e.g., "draft://foo.md")
     * @param content The content to write
     */
    fun writeFileFromString(path: String, content: String) {
        // Extract filename from draft:// path (removes protocol and any slashes)
        val filename = draftPathToFilename(path)

        val contentBytes = content.toByteArray(StandardCharsets.UTF_8)
        documents[filename] = contentBytes

        // Update or create LightVirtualFile
        val virtualFile = virtualFiles.getOrPut(filename) {
            LightVirtualFile(filename, content)
        }
        virtualFile.setContent(null, content, false)

        logger.info("Updated draft document: $filename")
    }

    /**
     * Check if a draft document exists.
     * @param path The draft:// path
     * @return true if the document exists
     */
    fun exists(path: String): Boolean {
        val filename = draftPathToFilename(path)
        return documents.containsKey(filename)
    }

    /**
     * Delete a draft document.
     * @param path The draft:// path
     */
    fun delete(path: String) {
        val filename = draftPathToFilename(path)
        documents.remove(filename)
        virtualFiles.remove(filename)
        logger.info("Deleted draft document: $filename")
    }

    /**
     * Get the virtual file for a draft document.
     * @param path The draft:// path
     * @return The LightVirtualFile, or null if not found
     */
    fun getVirtualFile(path: String): LightVirtualFile? {
        val filename = draftPathToFilename(path)
        return virtualFiles[filename]
    }

    /**
     * Get all draft document paths.
     * @return List of draft:// paths
     */
    fun getAllDraftPaths(): List<String> {
        return documents.keys.map { filenameToDraftPath(it) }
    }

    /**
     * Clear all draft documents (for cleanup).
     */
    fun clear() {
        documents.clear()
        virtualFiles.clear()
        logger.info("Cleared all draft documents")
    }
}




