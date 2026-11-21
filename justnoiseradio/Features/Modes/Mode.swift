// Mode.swift

import Foundation
import FamilyControls

struct Mode: Identifiable, Codable, Hashable {
    let id: UUID
    var name: String
    var selectedApps: FamilyActivitySelection
    
    init(id: UUID = UUID(), name: String, selectedApps: FamilyActivitySelection = FamilyActivitySelection()) {
        self.id = id
        self.name = name
        self.selectedApps = selectedApps
    }
    
    /// Only untouched installation presets qualify for removal; user-created names do not.
    var isUnusedLegacyPreset: Bool {
        let originalNames = [
            "AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA": "Noise",
            "BBBBBBBB-BBBB-BBBB-BBBB-BBBBBBBBBBBB": "Focus",
            "CCCCCCCC-CCCC-CCCC-CCCC-CCCCCCCCCCCC": "Sleep"
        ]
        return originalNames[id.uuidString] == name && !selectedApps.hasBlockingTargets
    }

    // Explicit Hashable implementation (because FamilyActivitySelection isn’t Hashable)
    static func == (lhs: Mode, rhs: Mode) -> Bool {
        lhs.id == rhs.id
    }
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}
