// kilocode_change - new file
/**
 * Draft path protocol constant.
 * Use this constant everywhere instead of hardcoding "draft://"
 */
export const DRAFT_PROTOCOL = "draft://"

/**
 * Check if a path is a draft path (starts with draft://)
 * @param path - The path to check
 * @returns true if the path is a draft path
 */
export function isDraftPath(path: string): boolean {
	return path.startsWith(DRAFT_PROTOCOL)
}

/**
 * Extract filename from a draft:// path.
 * Removes the protocol and any leading slashes.
 *
 * @param draftPath - The draft path (e.g., "draft://filename.md" or "draft:///filename.md")
 * @returns The filename without protocol or leading slashes (e.g., "filename.md")
 * @throws Error if the path is not a valid draft path
 */
export function draftPathToFilename(draftPath: string): string {
	if (!draftPath.startsWith(DRAFT_PROTOCOL)) {
		throw new Error(`Invalid draft path: ${draftPath}`)
	}
	const filename = draftPath.slice(DRAFT_PROTOCOL.length)
	// Remove any leading slashes
	return filename.startsWith("/") ? filename.slice(1) : filename
}

/**
 * Convert a filename to a draft:// path.
 * Removes any leading slashes from the filename before adding the protocol.
 *
 * @param filename - The filename (e.g., "filename.md" or "/filename.md")
 * @returns The draft path (e.g., "draft://filename.md")
 */
export function filenameToDraftPath(filename: string): string {
	// Remove any leading slashes from filename
	const cleanFilename = filename.startsWith("/") ? filename.slice(1) : filename
	return `${DRAFT_PROTOCOL}${cleanFilename}`
}
