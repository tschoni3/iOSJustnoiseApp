// AppFeatures.swift

/// Compile-time gates for product surfaces that are still under development.
///
/// Keep unfinished features merged into the main codebase and enable them only
/// in build configurations that explicitly opt in.
enum AppFeatures {
    #if CAPTURE_ENABLED
    static let captureEnabled = true
    #else
    static let captureEnabled = false
    #endif
}
