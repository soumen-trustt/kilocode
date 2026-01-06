# Create Draft Tool - Product Requirements Document

## Overview

The `create_draft` tool allows the AI agent to create temporary, in-memory planning documents that appear as editor tabs but are not saved to disk. These documents use a special `draft://` URI scheme and are discarded when the editor session ends. This enables the agent to create planning documents, implementation plans, and other ephemeral working documents for structured thinking.

## Core Functionality

1. **Create Draft Documents**: Agent can create a draft document with a title and initial content
2. **Draft Document Storage**: Documents are stored in-memory using a VS Code FileSystemProvider
3. **Editor Integration**: Documents appear as normal editor tabs and can be edited by the user
4. **File Tool Integration**: The `read_file` and `write_to_file` tools can read/write to draft documents via `draft://` paths
5. **Auto-approval**: Draft documents bypass approval prompts (they're ephemeral and safe)

## Architecture Requirements

### Tool System Architecture

The codebase uses a **class-based tool architecture** where all tools extend `BaseTool<TName>`. The tool system was refactored from function-based to class-based. All new tools must:

1. Extend `BaseTool<"tool_name">`
2. Implement `parseLegacy()` method for XML/legacy protocol support
3. Implement `execute()` method for core tool logic
4. Optionally override `handlePartial()` for streaming support
5. Export a singleton instance: `export const toolName = new ToolName()`

### Reference Implementation

Use `GenerateImageTool` (from main branch) as the reference:

- File: `src/core/tools/GenerateImageTool.ts`
- Extends `BaseTool<"generate_image">`
- Implements `parseLegacy()` and `execute()`
- Uses `ToolCallbacks` interface for callbacks
- Handles both native and legacy protocols

## Files to Create

### 1. Tool Implementation

**File**: `src/core/tools/CreateDraftTool.ts`

**Structure**:

```typescript
import { formatResponse } from "../prompts/responses"
import { Task } from "../task/Task"
import { getDraftFileSystem } from "../../services/planning"
import { BaseTool, ToolCallbacks } from "./BaseTool"
import type { ToolUse } from "../../shared/tools"

interface CreateDraftParams {
	title: string
	content: string
}

export class CreateDraftTool extends BaseTool<"create_draft"> {
	readonly name = "create_draft" as const

	parseLegacy(params: Partial<Record<string, string>>): CreateDraftParams {
		// Parse title and content from params
	}

	async execute(params: CreateDraftParams, task: Task, callbacks: ToolCallbacks): Promise<void> {
		// Validate title and content
		// Call getDraftFileSystem().createAndOpen(title, content)
		// Return success message with draft:// path
	}

	override async handlePartial(task: Task, block: ToolUse<"create_draft">): Promise<void> {
		// Show "Creating draft..." message during streaming
	}
}

export const createDraftTool = new CreateDraftTool()
```

**Key Requirements**:

- Validate `title` and `content` parameters (both required)
- Use `getDraftFileSystem()` from `../../services/planning`
- Call `fs.createAndOpen(title, content)` which returns a `draft://` path
- Return success message with instructions on how to use the draft
- Handle errors appropriately using `callbacks.handleError`
- Use `formatResponse.toolResult()` for success messages

### 2. Planning File System Provider

**File**: `src/services/planning/PlanningFileSystemProvider.ts`

**Purpose**: Implements VS Code FileSystemProvider for `draft://` scheme

**Key Components**:

- `DraftFileSystemProvider` class implementing `vscode.FileSystemProvider`
- In-memory storage: `Map<string, Uint8Array>` for document contents
- Helper methods:
    - `createAndOpen(name: string, content: string): Promise<string>` - Creates and opens document, returns `draft://` path
    - `readFileAsString(path: string): string | undefined` - Reads content for `read_file` tool
    - `writeFileFromString(path: string, content: string): void` - Writes content for `write_to_file` tool
- Singleton pattern: `getDraftFileSystem()` function
- Registration: `registerDraftFileSystem(context)` function

**Export**: `export { getDraftFileSystem, registerDraftFileSystem, DRAFT_SCHEME }`

### 3. Planning Service Index

**File**: `src/services/planning/index.ts`

**Content**:

```typescript
export { getDraftFileSystem, registerDraftFileSystem, DRAFT_SCHEME } from "./PlanningFileSystemProvider"
```

### 4. Tool Description (XML Protocol)

**File**: `src/core/prompts/tools/create-draft.ts`

**Content**: Returns XML-style tool description for legacy protocol. See existing file for exact format.

### 5. Native Tool Definition (JSON Protocol)

**File**: `src/core/prompts/tools/native-tools/create_draft.ts`

**Content**: Returns OpenAI-compatible tool definition. See existing file for exact format.

## Files to Modify

### 1. Tool Type Registration

**File**: `packages/types/src/tool.ts`

**Change**: Add `"create_draft"` to the `toolNames` array (if not already present)

```typescript
export const toolNames = [
	// ... existing tools ...
	"generate_image",
	"create_draft", // Add this line
] as const
```

### 2. Tool Description Registration

**File**: `src/core/prompts/tools/index.ts`

**Changes**:

1. Import: `import { getCreateDraftDescription } from "./create-draft"`
2. Add to `toolDescriptionMap`:

```typescript
const toolDescriptionMap: Record<string, (args: ToolArgs) => string | undefined> = {
	// ... existing tools ...
	create_draft: (args) => getCreateDraftDescription(args),
}
```

### 3. Native Tool Registration

**File**: `src/core/prompts/tools/native-tools/index.ts`

**Changes**:

1. Import: `import createDraft from "./create_draft"`
2. Add to `nativeTools` array:

```typescript
export const nativeTools = [
	// ... existing tools ...
	createDraft,
] satisfies OpenAI.Chat.ChatCompletionTool[]
```

### 4. Tool Execution Registration

**File**: `src/core/assistant-message/presentAssistantMessage.ts`

**Changes**:

1. Import (near top with other tool imports):

```typescript
import { createDraftTool } from "../tools/CreateDraftTool"
```

2. Add case in tool description switch (around line 472, after `generate_image`):

```typescript
case "generate_image":
	return `[${block.name} for '${block.params.path}']`
case "create_draft":
	return `[${block.name} for '${block.params.title}']`
default:
	return `[${block.name}]`
```

3. Add case in tool execution switch (around line 1122, after `generate_image`):

```typescript
case "generate_image":
	await checkpointSaveAndMark(cline)
	await generateImageTool.handle(cline, block as ToolUse<"generate_image">, {
		askApproval,
		handleError,
		pushToolResult,
		removeClosingTag,
		toolProtocol,
	})
	break
case "create_draft":
	await createDraftTool.handle(cline, block as ToolUse<"create_draft">, {
		askApproval,
		handleError,
		pushToolResult,
		removeClosingTag,
		toolProtocol,
	})
	break
default: {
```

### 5. Extension Registration

**File**: `src/extension.ts`

**Changes**:

1. Import: `import { registerDraftFileSystem } from "./services/planning"`
2. Call in activation function: `registerDraftFileSystem(context)`

### 6. Draft Path Support in ReadFileTool

**File**: `src/core/tools/ReadFileTool.ts`

**Changes**:

1. Import: `import { getDraftFileSystem } from "../../services/planning"`

2. Add helper functions (near top):

```typescript
// Helper functions for draft document paths
function isDraftPath(path: string): boolean {
	return path.startsWith("draft://")
}

function parseDraftPath(path: string): string {
	// "draft://foo.md" -> "/foo.md"
	return "/" + path.slice("draft://".length)
}
```

3. In approval loop (around line 178), skip approval for draft paths:

```typescript
if (fileResult.status === "pending") {
	// Skip approval for draft documents (auto-approved)
	if (isDraftPath(relPath)) {
		updateFileResult(relPath, {
			status: "approved",
		})
		continue
	}
	// ... rest of approval logic
}
```

4. In file processing loop (around line 333), handle draft paths:

```typescript
for (const fileResult of fileResults) {
	if (fileResult.status !== "approved") continue

	const relPath = fileResult.path

	// Check if this is a planning document
	if (isDraftPath(relPath)) {
		const draftPath = parseDraftPath(relPath)
		const fs = getDraftFileSystem()
		const content = fs.readFileAsString(draftPath)

		if (content === undefined) {
			// Handle error - document not found
			updateFileResult(relPath, {
				status: "error",
				error: `Planning document not found: ${relPath}`,
				xmlContent: `<file><path>${relPath}</path><error>Planning document not found</error></file>`,
				nativeContent: `File: ${relPath}\nError: Planning document not found`,
			})
			await handleError(`reading planning document ${relPath}`, new Error("Planning document not found"))
			continue
		}

		// Process content similar to regular files (add line numbers, etc.)
		const numberedContent = addLineNumbers(content)
		const totalLines = content.split("\n").length

		// Track file read
		await task.fileContextTracker.trackFileContext(relPath, "read_tool" as RecordSource)

		const lineRangeAttr = ` lines="1-${totalLines}"`
		const xmlInfo = totalLines > 0 ? `<content${lineRangeAttr}>\n${numberedContent}</content>\n` : `<content/>`
		const nativeInfo =
			totalLines > 0
				? `File: ${relPath}\nLines: 1-${totalLines}\n\n${numberedContent}`
				: `File: ${relPath}\n(empty file)`

		updateFileResult(relPath, {
			xmlContent: `<file><path>${relPath}</path>\n${xmlInfo}</file>`,
			nativeContent: nativeInfo,
		})
		continue
	}

	// ... rest of regular file processing
}
```

### 7. Draft Path Support in WriteToFileTool

**File**: `src/core/tools/WriteToFileTool.ts`

**Changes**:

1. Import: `import { getDraftFileSystem } from "../../services/planning"`

2. Early return for draft paths (after parameter validation, before accessAllowed check):

```typescript
// Check if this is a draft document
if (relPath.startsWith("draft://")) {
	const draftPath = "/" + relPath.slice("draft://".length)
	const fs = getDraftFileSystem()

	// Write to draft document
	fs.writeFileFromString(draftPath, newContent)

	// Track file edit operation
	await task.fileContextTracker.trackFileContext(relPath, "roo_edited" as RecordSource)

	task.didEditFile = true

	pushToolResult(formatResponse.toolResult(`Updated planning document "${relPath}"`))
	return
}
```

### 8. Draft Path Support in simpleReadFileTool (if it exists)

**File**: `src/core/tools/simpleReadFileTool.ts`

**Changes**: Similar to ReadFileTool - add draft path detection and auto-approval, then handle reading from draft file system.

### 9. Tool Type Definitions (if needed)

**File**: `src/shared/tools.ts`

**Check**: Ensure `OpenPlanningDocumentToolUse` interface exists (it should already be there):

```typescript
export interface OpenPlanningDocumentToolUse extends ToolUse {
	name: "create_draft"
	params: Partial<Pick<Record<ToolParamName, string>, "title" | "content">>
}
```

**Also check**: `TOOL_DISPLAY_NAMES` and `TOOL_GROUPS` should include `create_draft` (likely already present).

## Implementation Notes

### Important Constraints

1. **DO NOT modify**:

    - `src/core/prompts/responses.ts` - This is core infrastructure
    - `src/core/tools/BaseTool.ts` - This is core infrastructure
    - Any other core tool infrastructure files

2. **DO follow the pattern**:

    - Use `BaseTool` class architecture (not function-based)
    - Use `ToolCallbacks` interface for callbacks
    - Pass `toolProtocol` to formatResponse functions that accept it (optional parameter)
    - Handle both native and legacy protocols via `parseLegacy()`

3. **Draft Path Format**:

    - Created documents return: `draft://{title}.md`
    - Internal storage uses: `/{title}.md`
    - Helper functions convert between formats

4. **Error Handling**:

    - Use `callbacks.handleError()` for errors
    - Use `task.sayAndCreateMissingParamError()` for missing parameters
    - Use `formatResponse.toolResult()` for success messages
    - Use `formatResponse.toolError()` for error messages (optional toolProtocol parameter)

5. **File Tracking**:
    - Use `task.fileContextTracker.trackFileContext()` for read operations
    - Use `task.fileContextTracker.trackFileContext(relPath, "roo_edited")` for write operations
    - Set `task.didEditFile = true` when writing to drafts

## Testing Scenarios

### Test Prompt for Debug Mode

```
Please test the create_draft tool by:

1. Create a draft document with the title "test-plan" and some initial content (a simple markdown document with a few sections)

2. Read the draft document back using read_file with the draft:// path that was returned

3. Update the draft document using write_to_file with the draft:// path, adding new content or modifying existing content

4. Read it again to verify your changes were saved

5. Create a second draft document with a different title to verify multiple drafts can coexist

6. Read both drafts to verify they are separate documents

7. Try to read a non-existent draft:// path to verify error handling works correctly

Please confirm that:
- Drafts appear as editor tabs
- Drafts can be read and written to
- Multiple drafts can exist simultaneously
- Error handling works for missing drafts
- The draft:// paths work correctly with read_file and write_to_file tools
```

## Success Criteria

1. ✅ Tool can be called via `create_draft` with `title` and `content` parameters
2. ✅ Draft document appears as editor tab
3. ✅ Draft document can be read via `read_file` with `draft://` path
4. ✅ Draft document can be written via `write_to_file` with `draft://` path
5. ✅ Draft documents auto-approve (no approval prompts)
6. ✅ Multiple draft documents can coexist
7. ✅ Error handling works for missing drafts
8. ✅ Tool works with both XML and native protocols
9. ✅ Tool description appears correctly in tool lists
10. ✅ Native tool definition is properly registered

## Files Summary

**New Files**:

- `src/core/tools/CreateDraftTool.ts`
- `src/services/planning/PlanningFileSystemProvider.ts`
- `src/services/planning/index.ts`
- `src/core/prompts/tools/create-draft.ts` (may already exist)
- `src/core/prompts/tools/native-tools/create_draft.ts` (may already exist)

**Modified Files**:

- `packages/types/src/tool.ts` - Add tool name
- `src/core/prompts/tools/index.ts` - Register description
- `src/core/prompts/tools/native-tools/index.ts` - Register native tool
- `src/core/assistant-message/presentAssistantMessage.ts` - Add tool cases
- `src/core/tools/ReadFileTool.ts` - Add draft:// support
- `src/core/tools/WriteToFileTool.ts` - Add draft:// support
- `src/core/tools/simpleReadFileTool.ts` - Add draft:// support (if exists)
- `src/extension.ts` - Register file system provider

## Reference Files to Study

- `src/core/tools/GenerateImageTool.ts` - Class-based tool implementation pattern
- `src/core/tools/ReadFileTool.ts` - How to handle file reading with different protocols
- `src/core/tools/WriteToFileTool.ts` - How to handle file writing
- `src/core/assistant-message/presentAssistantMessage.ts` - How tools are registered and called



