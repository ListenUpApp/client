import SwiftUI
import Shared

// MARK: - Kotlin Value Class Bridging
//
// Kotlin value classes (inline classes) are represented as opaque `Any` types
// in Swift because Objective-C cannot represent inline/value types.
//
// The underlying value is accessible via the object's description, which calls
// Kotlin's toString(). All our value classes implement toString() to return
// their wrapped value.
//
// This file provides type-safe extensions to extract the underlying values.
//
// Reference: shared/src/commonMain/kotlin/com/calypsan/listenup/client/core/ValueClasses.kt

// MARK: - User Extensions

extension User {
    /// The user's ID as a Swift String.
    var idString: String {
        String(describing: id)
    }
}

// MARK: - Book Extensions

extension Book {
    /// The book's ID as a Swift String.
    var idString: String {
        String(describing: id)
    }
}

// MARK: - Series Extensions

extension Series {
    /// The series ID as a Swift String.
    var idString: String {
        String(describing: id)
    }
}

// MARK: - Contributor Extensions

extension Contributor {
    /// The contributor's ID as a Swift String.
    var idString: String {
        String(describing: id)
    }
}

// MARK: - Avatar Color Helper

/// Generate a consistent avatar color based on user ID.
/// Uses hue rotation to create visually distinct colors.
func avatarColorForUserId(_ userId: String) -> Color {
    let hash = abs(userId.hashValue)
    let hue = Double(hash % 360) / 360.0
    return Color(hue: hue, saturation: 0.4, brightness: 0.65)
}
