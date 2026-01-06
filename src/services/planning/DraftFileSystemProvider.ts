// kilocode_change - new file
import * as vscode from "vscode"
import { filenameToDraftPath } from "./draftPaths"

export const DRAFT_SCHEME = "draft"

/**
 * File system provider for draft:// documents.
 * Stores documents in-memory and makes them available as editor tabs.
 */
class DraftFileSystemProvider implements vscode.FileSystemProvider {
	private readonly _emitter = new vscode.EventEmitter<vscode.FileChangeEvent[]>()
	private readonly _documents = new Map<string, Uint8Array>()

	readonly onDidChangeFile: vscode.Event<vscode.FileChangeEvent[]> = this._emitter.event

	watch(_uri: vscode.Uri, _options: { recursive: boolean; excludes: string[] }): vscode.Disposable {
		// No-op: we don't support watching draft documents
		return new vscode.Disposable(() => {})
	}

	stat(uri: vscode.Uri): vscode.FileStat {
		// VS Code URIs have paths starting with /, but we store without leading slash
		const filename = uri.path.startsWith("/") ? uri.path.slice(1) : uri.path
		const content = this._documents.get(filename)

		if (content === undefined) {
			throw vscode.FileSystemError.FileNotFound(uri)
		}

		return {
			type: vscode.FileType.File,
			ctime: Date.now(),
			mtime: Date.now(),
			size: content.length,
		}
	}

	readDirectory(_uri: vscode.Uri): [string, vscode.FileType][] {
		// Draft documents don't support directories
		throw vscode.FileSystemError.FileNotFound()
	}

	createDirectory(_uri: vscode.Uri): void {
		// Draft documents don't support directories
		throw vscode.FileSystemError.NoPermissions()
	}

	readFile(uri: vscode.Uri): Uint8Array {
		// VS Code URIs have paths starting with /, but we store without leading slash
		const filename = uri.path.startsWith("/") ? uri.path.slice(1) : uri.path
		const content = this._documents.get(filename)

		if (content === undefined) {
			throw vscode.FileSystemError.FileNotFound(uri)
		}

		return content
	}

	writeFile(uri: vscode.Uri, content: Uint8Array, _options: { create: boolean; overwrite: boolean }): void {
		// VS Code URIs have paths starting with /, but we store without leading slash
		const filename = uri.path.startsWith("/") ? uri.path.slice(1) : uri.path
		const wasNew = !this._documents.has(filename)
		this._documents.set(filename, content)

		// Emit file change event
		const event: vscode.FileChangeEvent = {
			type: wasNew ? vscode.FileChangeType.Created : vscode.FileChangeType.Changed,
			uri,
		}
		this._emitter.fire([event])
	}

	delete(uri: vscode.Uri): void {
		// VS Code URIs have paths starting with /, but we store without leading slash
		const filename = uri.path.startsWith("/") ? uri.path.slice(1) : uri.path
		if (!this._documents.has(filename)) {
			throw vscode.FileSystemError.FileNotFound(uri)
		}

		this._documents.delete(filename)

		// Emit file change event
		const event: vscode.FileChangeEvent = {
			type: vscode.FileChangeType.Deleted,
			uri,
		}
		this._emitter.fire([event])
	}

	rename(_oldUri: vscode.Uri, _newUri: vscode.Uri, _options: { overwrite: boolean }): void {
		// Draft documents don't support rename
		throw vscode.FileSystemError.NoPermissions()
	}

	/**
	 * Create a new draft document and open it in the editor.
	 * @param name - The name/title of the document (will be used as filename)
	 * @param content - Initial content of the document
	 * @returns The draft:// URI path (e.g., "draft://filename.md")
	 */
	async createAndOpen(name: string, content: string): Promise<string> {
		// Ensure name has .md extension
		const filename = name.endsWith(".md") ? name : `${name}.md`

		// Store the document internally without leading slash
		const contentBytes = new TextEncoder().encode(content)
		this._documents.set(filename, contentBytes)

		// VS Code URIs need path starting with /, so use /filename for URI
		const uri = vscode.Uri.parse(`${DRAFT_SCHEME}:/${filename}`)

		// Emit file change event
		const event: vscode.FileChangeEvent = {
			type: vscode.FileChangeType.Created,
			uri,
		}
		this._emitter.fire([event])

		// Open the document in the editor
		await vscode.window.showTextDocument(uri, { preview: false })

		// Return the draft:// path for use in tools (double slash, no slash in filename)
		return filenameToDraftPath(filename)
	}
}

// Singleton instance
let draftFileSystemProvider: DraftFileSystemProvider | undefined

/**
 * Get the singleton draft file system provider instance.
 */
export function getDraftFileSystem(): DraftFileSystemProvider {
	if (!draftFileSystemProvider) {
		draftFileSystemProvider = new DraftFileSystemProvider()
	}
	return draftFileSystemProvider
}

/**
 * Register the draft file system provider with VS Code.
 * @param context - VS Code extension context
 */
export function registerDraftFileSystem(context: vscode.ExtensionContext): void {
	const provider = getDraftFileSystem()
	context.subscriptions.push(
		vscode.workspace.registerFileSystemProvider(DRAFT_SCHEME, provider, {
			isCaseSensitive: true,
		}),
	)
}
