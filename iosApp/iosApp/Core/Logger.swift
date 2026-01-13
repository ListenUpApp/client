import Foundation
import os

/// Unified logging for ListenUp iOS app.
///
/// Uses Apple's unified logging system (OSLog) for consistent log output
/// that appears in Xcode console and Console.app.
///
/// This logger is designed to complement the kotlin-logging output from
/// the shared KMP module - both use the same underlying OSLog system.
///
/// Usage:
/// ```swift
/// // Using the shared logger
/// Log.debug("User tapped login button")
/// Log.error("Failed to load data", error: error)
///
/// // Creating a subsystem-specific logger
/// let networkLog = Logger.forSubsystem("Network")
/// networkLog.debug("Request started")
/// ```
///
/// Log levels:
/// - `debug`: Detailed debugging info (only visible when debugging or in Console.app)
/// - `info`: General information about app operation
/// - `warning`: Potential issues that don't prevent operation
/// - `error`: Errors that affect functionality
/// - `fault`: Critical errors (system-level issues)
enum Log {
    /// Subsystem identifier for the app (bundle identifier)
    private static let subsystem = Bundle.main.bundleIdentifier ?? "com.calypsan.listenup"

    /// Default logger for general app logging
    private static let defaultLogger = os.Logger(subsystem: subsystem, category: "App")

    // MARK: - Convenience Methods

    /// Log a debug message.
    /// Debug messages are only visible in Xcode or Console.app with debug logging enabled.
    static func debug(
        _ message: String,
        file: String = #file,
        function: String = #function,
        line: Int = #line
    ) {
        let category = categoryFromFile(file)
        os.Logger(subsystem: subsystem, category: category).debug("\(message, privacy: .public)")
    }

    /// Log an informational message.
    static func info(
        _ message: String,
        file: String = #file,
        function: String = #function,
        line: Int = #line
    ) {
        let category = categoryFromFile(file)
        os.Logger(subsystem: subsystem, category: category).info("\(message, privacy: .public)")
    }

    /// Log a warning message.
    static func warning(
        _ message: String,
        file: String = #file,
        function: String = #function,
        line: Int = #line
    ) {
        let category = categoryFromFile(file)
        os.Logger(subsystem: subsystem, category: category).warning("\(message, privacy: .public)")
    }

    /// Log an error message.
    static func error(
        _ message: String,
        error: Error? = nil,
        file: String = #file,
        function: String = #function,
        line: Int = #line
    ) {
        let category = categoryFromFile(file)
        let logger = os.Logger(subsystem: subsystem, category: category)

        if let error = error {
            logger.error("\(message, privacy: .public): \(error.localizedDescription, privacy: .public)")
        } else {
            logger.error("\(message, privacy: .public)")
        }
    }

    /// Log a critical fault message.
    /// Use sparingly - only for critical system-level issues.
    static func fault(
        _ message: String,
        file: String = #file,
        function: String = #function,
        line: Int = #line
    ) {
        let category = categoryFromFile(file)
        os.Logger(subsystem: subsystem, category: category).fault("\(message, privacy: .public)")
    }

    // MARK: - Category Extraction

    /// Extracts a category name from the file path (uses the file name without extension).
    private static func categoryFromFile(_ file: String) -> String {
        URL(fileURLWithPath: file).deletingPathExtension().lastPathComponent
    }

    // MARK: - Subsystem-Specific Loggers

    /// Create a logger for a specific subsystem/category.
    ///
    /// Example:
    /// ```swift
    /// let networkLog = Log.forSubsystem("Network")
    /// networkLog.debug("Starting request...")
    /// ```
    static func forSubsystem(_ category: String) -> os.Logger {
        os.Logger(subsystem: subsystem, category: category)
    }
}

// MARK: - Logger Extension for Structured Logging

extension os.Logger {
    /// Log a debug message with optional metadata.
    func debug(_ message: String, metadata: [String: Any]? = nil) {
        if let metadata = metadata {
            self.debug("\(message, privacy: .public) | \(metadata.description, privacy: .public)")
        } else {
            self.debug("\(message, privacy: .public)")
        }
    }

    /// Log an error with optional Error object.
    func error(_ message: String, error: Error?) {
        if let error = error {
            self.error("\(message, privacy: .public): \(error.localizedDescription, privacy: .public)")
        } else {
            self.error("\(message, privacy: .public)")
        }
    }
}

// MARK: - Preview/Debug Helpers

#if DEBUG
extension Log {
    /// Print all log output to console as well (for debugging).
    /// In production, logs go only to OSLog.
    static func debugPrint(_ message: String) {
        print("[ListenUp] \(message)")
    }
}
#endif
