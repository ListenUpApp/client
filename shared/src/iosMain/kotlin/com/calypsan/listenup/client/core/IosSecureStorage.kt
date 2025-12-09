package com.calypsan.listenup.client.core

import kotlinx.cinterop.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.Foundation.*
import platform.Security.*

/**
 * iOS implementation of SecureStorage using Keychain Services.
 *
 * Features:
 * - Hardware-backed encryption via Secure Enclave (when available)
 * - Items accessible after first device unlock (kSecAttrAccessibleAfterFirstUnlock)
 * - Thread-safe operations via Dispatchers.Default
 * - Proper memory management with memScoped
 *
 * Ready for biometric protection upgrade (change to kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly).
 */
@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
@Suppress("CAST_NEVER_SUCCEEDS")
internal class IosSecureStorage : SecureStorage {
    override suspend fun save(
        key: String,
        value: String,
    ) = withContext(Dispatchers.Default) {
        val data =
            (value as NSString).dataUsingEncoding(NSUTF8StringEncoding)
                ?: throw IllegalArgumentException("Failed to encode value to UTF-8")

        val query =
            mapOf(
                kSecClass to kSecClassGenericPassword,
                kSecAttrAccount to key,
                kSecAttrAccessible to kSecAttrAccessibleAfterFirstUnlock,
                kSecValueData to data,
            ) as Map<Any?, *>

        // Delete existing item (if any)
        SecItemDelete(query as CFDictionaryRef)

        // Add new item
        val status = SecItemAdd(query as CFDictionaryRef, null)
        if (status != errSecSuccess) {
            throw SecurityException("Failed to save to keychain: $status")
        }
    }

    override suspend fun read(key: String): String? =
        withContext(Dispatchers.Default) {
            val query =
                mapOf(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrAccount to key,
                    kSecReturnData to kCFBooleanTrue,
                    kSecMatchLimit to kSecMatchLimitOne,
                ) as Map<Any?, *>

            memScoped {
                val result = alloc<CFTypeRefVar>()
                val status = SecItemCopyMatching(query as CFDictionaryRef, result.ptr)

                if (status == errSecSuccess) {
                    val data = CFBridgingRelease(result.value) as? NSData
                    return@withContext data?.let {
                        NSString.create(it, NSUTF8StringEncoding) as? String
                    }
                }

                // errSecItemNotFound is expected for non-existent keys
                if (status == errSecItemNotFound) {
                    return@withContext null
                }

                throw SecurityException("Failed to read from keychain: $status")
            }
        }

    override suspend fun delete(key: String) =
        withContext(Dispatchers.Default) {
            val query =
                mapOf(
                    kSecClass to kSecClassGenericPassword,
                    kSecAttrAccount to key,
                ) as Map<Any?, *>

            val status = SecItemDelete(query as CFDictionaryRef)
            // errSecItemNotFound is acceptable - key already doesn't exist
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw SecurityException("Failed to delete from keychain: $status")
            }
        }

    override suspend fun clear() =
        withContext(Dispatchers.Default) {
            val query =
                mapOf(
                    kSecClass to kSecClassGenericPassword,
                ) as Map<Any?, *>

            val status = SecItemDelete(query as CFDictionaryRef)
            // errSecItemNotFound is acceptable - nothing to delete
            if (status != errSecSuccess && status != errSecItemNotFound) {
                throw SecurityException("Failed to clear keychain: $status")
            }
        }
}

/**
 * Custom exception for iOS Keychain errors.
 */
class SecurityException(
    message: String,
) : Exception(message)
