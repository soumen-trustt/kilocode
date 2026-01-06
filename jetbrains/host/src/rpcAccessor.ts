// Copyright 2009-2025 Weibo, Inc.
// SPDX-FileCopyrightText: 2025 Weibo, Inc.
//
// SPDX-License-Identifier: Apache-2.0

import { IRPCProtocol } from "../deps/vscode/vs/workbench/services/extensions/common/proxyIdentifier.js"

/**
 * Global accessor for the RPC protocol.
 * This allows components to access the RPC protocol to call methods on the Kotlin plugin side.
 */
let rpcProtocol: IRPCProtocol | null = null

/**
 * Set the RPC protocol instance.
 * This should be called during extension initialization.
 */
export function setRpcProtocol(protocol: IRPCProtocol): void {
	rpcProtocol = protocol
}

/**
 * Get the RPC protocol instance.
 * @returns The RPC protocol or null if not initialized
 */
export function getRpcProtocol(): IRPCProtocol | null {
	return rpcProtocol
}

/**
 * Invoke a method on the Kotlin plugin side via RPC.
 * @param methodName The name of the method to invoke (e.g., "MainThreadLanguageModelTools.createDraft")
 * @param args The arguments to pass to the method
 * @returns A promise that resolves with the result
 */
export async function invokeRpcMethod<T = any>(methodName: string, args: any[]): Promise<T> {
	const protocol = getRpcProtocol()
	if (!protocol) {
		throw new Error("RPC protocol not initialized")
	}
	// Use getProxy to get a proxy object that can invoke methods
	// The method will be called on the proxy object
	const proxy = protocol.getProxy(null as any)
	// @ts-expect-error - invoke method exists on the proxied object
	return proxy[methodName](...args)
}
