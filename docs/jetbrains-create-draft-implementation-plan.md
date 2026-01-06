# JetBrains `create_draft` Tool Implementation Plan

## Current State

### ✅ Already Implemented (Kotlin Side)

1. **DraftFileSystemProvider.kt** (`jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/filesystem/DraftFileSystemProvider.kt`)

    - In-memory document storage using `ConcurrentHashMap`
    - `createAndOpen()` method to create and open draft documents
    - `readFileAsString()` and `writeFileFromString()` methods
    - Uses `LightVirtualFile` for editor integration
    - Singleton pattern for application-wide access

2. **DraftPaths.kt** (`jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/filesystem/DraftPaths.kt`)

    - `DRAFT_PROTOCOL` constant
    - `isDraftPath()` helper
    - `draftPathToFilename()` converter
    - `filenameToDraftPath()` converter

3. **CreateDraftTool.kt** (`jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/tools/CreateDraftTool.kt`)

    - Tool handler class
    - Parameter validation (title, content)
    - Returns result map with message and draftPath

4. **MainThreadFileSystemShape.kt** - Draft file system integration

    - `stat()` handles draft:// URIs
    - `readFile()` handles draft:// URIs
    - `writeFile()` handles draft:// URIs

5. **EditorAndDocManager.kt** - Editor tab integration
    - `getDocumentContent()` handles draft:// URIs
    - `openEditor()` opens draft documents in editor tabs
    - `openDocument()` reads draft document content

### ✅ Already Implemented (TypeScript Side)

1. **CreateDraftTool.ts** (`src/core/tools/CreateDraftTool.ts`)

    - Tool execution class
    - Calls `getDraftFileSystem().createAndOpen()`

2. **DraftFileSystemProvider.ts** (`src/services/planning/DraftFileSystemProvider.ts`)

    - VS Code FileSystemProvider implementation
    - `createAndOpen()` opens document via `vscode.window.showTextDocument()`

3. **ReadFileTool.ts** - Draft document reading support
4. **WriteToFileTool.ts** - Draft document writing support

### ⚠️ Problem

The TypeScript code uses `vscode.workspace.fs` and `vscode.window.showTextDocument()` directly. In JetBrains, these VS Code shims route through the RPC protocol to the Kotlin plugin. The key question is: **does the RPC properly handle `registerFileSystemProvider` and `showTextDocument` for custom schemes like `draft://`?**

## Required Investigation & Implementation

### Phase 1: Verify RPC Infrastructure

**1.1 Check if `registerFileSystemProvider` is implemented in host**

File: `jetbrains/host/src/rpcManager.ts`

Need to verify:

```typescript
// Does this exist?
vscode.workspace.registerFileSystemProvider(scheme: string, provider: any, options?: { isCaseSensitive?: boolean })
```

**1.2 Check if `showTextDocument` routes correctly**

File: `jetbrains/host/src/rpcManager.ts`

Need to verify:

```typescript
// Does this properly call Kotlin's EditorAndDocManager.openEditor()?
vscode.window.showTextDocument(uri: vscode.Uri, options?: { preview?: boolean })
```

### Phase 2: Fix RPC Gaps (if needed)

**2.1 Implement missing file system provider registration**

If `registerFileSystemProvider` isn't implemented, need to add:

```typescript
// In rpcManager.ts
this.rpcProtocol.set(MainContext.MainThreadWorkspace, {
	// ... existing methods ...
	$registerFileSystemProvider(scheme: string, provider: any): void {
		// Route to Kotlin's MainThreadFileSystem
	},
	$unregisterFileSystemProvider(scheme: string): void {
		// Cleanup
	},
})
```

**2.2 Ensure showTextDocument opens draft:// URIs**

The Kotlin side already handles `draft://` in `EditorAndDocManager.openEditor()`. Need to verify the RPC properly converts the URI and calls this method.

### Phase 3: Testing & Validation

**3.1 Manual testing checklist**

- [ ] Run JetBrains IDE with plugin
- [ ] Use create_draft tool in chat
- [ ] Verify draft document opens in editor tab
- [ ] Verify read_file works on draft:// path
- [ ] Verify write_to_file updates draft document
- [ ] Verify multiple draft documents can be created

**3.2 Edge cases to test**

- Draft with same name twice (should update, not duplicate)
- Close and reopen draft tab
- Memory cleanup when IDE restarts

## Technical Details

### How it should work (VS Code)

```
TypeScript CreateDraftTool.execute()
  → getDraftFileSystem().createAndOpen(title, content)
  → DraftFileSystemProvider.createAndOpen()
    → Store document in memory Map
    → vscode.workspace.registerFileSystemProvider() already done at extension activation
    → vscode.window.showTextDocument(draft://uri)  ← Opens editor tab
  → Returns draft://filename.md path
```

### How it should work (JetBrains)

```
TypeScript CreateDraftTool.execute()
  → getDraftFileSystem().createAndOpen(title, content)  ← VS Code API called
  → (RPC) vscode.workspace.fs.writeFile(draft://filename.md)
  → (RPC) vscode.window.showTextDocument(draft://filename.md)
  → Kotlin MainThreadFileSystem.writeFile()  ← Handled
  → Kotlin EditorAndDocManager.openEditor()  ← Opens editor tab
  → Returns draft://filename.md path
```

### Key RPC Methods Involved

1. **FileSystemProvider RPC** (if implemented)

    - `MainThreadWorkspace.$registerFileSystemProvider()`
    - `MainThreadFileSystem.$stat()`
    - `MainThreadFileSystem.$readFile()`
    - `MainThreadFileSystem.$writeFile()`

2. **Editor RPC**
    - `MainThreadEditors.$showTextDocument()` or similar

## Estimated Effort

| Task                                    | Hours           |
| --------------------------------------- | --------------- |
| Investigate existing RPC implementation | 2               |
| Fix any missing RPC methods             | 4               |
| Test create_draft flow end-to-end       | 2               |
| Fix issues found during testing         | 2-4             |
| **Total**                               | **10-12 hours** |

## Alternative Approach (Simpler)

If RPC bridging proves difficult, an alternative is to:

1. **Add a new RPC method specifically for create_draft**:

```kotlin
// In MainThreadLanguageModelToolsShape.kt
fun createDraft(name: String, content: String): String
```

2. **Call it from TypeScript via existing invokeTool RPC**:

```typescript
// The existing $invokeTool method could handle create_draft specially
```

This would bypass the FileSystemProvider RPC entirely and go directly through the tools RPC, which is already working.

## Recommendation

Start with Phase 1 (Investigation) to understand if the existing RPC infrastructure already supports custom file system providers. If not, the "Alternative Approach" using a direct `createDraft` RPC method would be simpler to implement.

## Files Modified

### Already Modified (in this branch)

- `jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/filesystem/DraftFileSystemProvider.kt` (new)
- `jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/filesystem/DraftPaths.kt` (new)
- `jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/tools/CreateDraftTool.kt` (new)
- `jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/actors/MainThreadFileSystemShape.kt` (modified)
- `jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/editor/EditorAndDocManager.kt` (modified)
- `jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/actors/MainThreadLanguageModelToolsShape.kt` (modified)
- `jetbrains/plugin/src/main/kotlin/ai/kilocode/jetbrains/core/RPCManager.kt` (modified)

### May Need Modification

- `jetbrains/host/src/rpcManager.ts` - May need to implement `registerFileSystemProvider`
- `src/core/tools/CreateDraftTool.ts` - May need JetBrains detection/branching
