// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

package ai.kilocode.jetbrains.actors

import ai.kilocode.jetbrains.tools.CreateDraftTool
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Language model tools service interface.
 * Corresponds to the MainThreadLanguageModelTools interface in VSCode.
 */
interface MainThreadLanguageModelToolsShape : Disposable {
    /**
     * Gets all available tool list.
     */
    fun getTools(): List<Map<String, Any?>>

    /**
     * Invokes the specified tool.
     * @param dto Tool invocation parameters
     * @param token Cancellation token
     */
    fun invokeTool(dto: Map<String, Any?>, token: Any? = null): Map<String, Any?>

    /**
     * Calculates the number of tokens for the given input.
     * @param callId Call ID
     * @param input Input content
     * @param token Cancellation token
     */
    fun countTokensForInvocation(callId: String, input: String, token: Any?): Int

    /**
     * Registers a tool.
     * @param id Tool ID
     */
    fun registerTool(id: String)

    /**
     * Unregisters a tool.
     * @param name Tool name
     */
    fun unregisterTool(name: String)

    /**
     * Creates a draft document in the IDE.
     * @param name The name/title of the draft document
     * @param content The initial content of the draft
     * @return Result map with success message and draft path
     */
    fun createDraft(name: String, content: String): Map<String, Any?>
}

/**
 * Implementation of the language model tools service.
 */
class MainThreadLanguageModelTools(private val project: com.intellij.openapi.project.Project) : MainThreadLanguageModelToolsShape {

    private val logger = logger<MainThreadLanguageModelTools>()
    private val tools = ConcurrentHashMap<String, ToolInfo>()
    
    // Cache tool handlers to avoid creating new instances on every invocation
    private val createDraftTool by lazy { CreateDraftTool(project) }

    /**
     * Tool information
     */
    private data class ToolInfo(
        val id: String,
        val registered: Boolean = true,
    )

    override fun getTools(): List<Map<String, Any?>> {
        logger.info("Get available language model tool list")
        // Return the list of registered tools
        return tools.values.filter { it.registered }.map {
            mapOf("id" to it.id)
        }
    }

    override fun invokeTool(dto: Map<String, Any?>, token: Any?): Map<String, Any?> {
        val toolId = dto["id"] as? String ?: throw IllegalArgumentException("Tool ID cannot be empty")
        val params = dto["params"] ?: emptyMap<String, Any?>()

        logger.info("Invoke language model tool: $toolId")
        val toolInfo = tools[toolId] ?: throw IllegalArgumentException("Tool with ID $toolId not found")

        if (!toolInfo.registered) {
            throw IllegalStateException("Tool $toolId is not registered")
        }

        // Route to specific tool handlers
        @Suppress("UNCHECKED_CAST")
        val paramsMap = params as? Map<String, Any?> ?: emptyMap()
        
        return when (toolId) {
            "create_draft" -> createDraftTool.handle(paramsMap)
            else -> {
                // Default: return mock result for unknown tools
                // In the actual implementation, it may need to call the real tool in the extension process via RPC.
                mapOf(
                    "result" to "Tool $toolId invoked successfully",
                    "id" to toolId,
                )
            }
        }
    }

    override fun countTokensForInvocation(callId: String, input: String, token: Any?): Int {
        logger.info("Calculate token count for tool invocation $callId")

        // The actual token count should be calculated here. Currently returns a mock result.
        // In the actual implementation, it may need to use a specific algorithm or service to calculate the token count.
        return input.length / 4 + 1 // Simple mock token calculation
    }

    override fun registerTool(id: String) {
        logger.info("Register language model tool: $id")

        tools[id] = ToolInfo(id, true)
    }

    override fun unregisterTool(name: String) {
        logger.info("Unregister language model tool: $name")

        if (tools.containsKey(name)) {
            tools[name] = tools[name]!!.copy(registered = false)
        } else {
            logger.warn("Attempting to unregister non-existent tool: $name")
        }
    }

    override fun createDraft(name: String, content: String): Map<String, Any?> {
        logger.info("Creating draft document: $name")
        return createDraftTool.handle(mapOf("title" to name, "content" to content))
    }

    override fun dispose() {
        logger.info("Dispose MainThreadLanguageModelTools resources")
        tools.clear()
    }
}
