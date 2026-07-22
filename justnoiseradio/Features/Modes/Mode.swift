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
    
    // Explicit Hashable implementation (because FamilyActivitySelection isn’t Hashable)
    static func == (lhs: Mode, rhs: Mode) -> Bool {
        lhs.id == rhs.id
    }
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(id)
    }
}
