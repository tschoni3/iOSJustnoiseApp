import SwiftUI
import FamilyControls

struct FamilyActivityPickerView: View {
    @Binding var isPresented: Bool
    @Binding var mode: Mode
    @EnvironmentObject var nfcViewModel: NFCViewModel

    var body: some View {
        NavigationView {
            FamilyActivityPicker(selection: $mode.selectedApps)
                .navigationTitle("Select to Block")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Cancel") {
                            isPresented = false
                        }
                    }
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Save") {
                            // Save updated mode to ViewModel
                            nfcViewModel.updateMode(mode)
                            isPresented = false
                        }
                        .disabled(isSelectionEmpty)
                    }
                }
        }
    }

    private var isSelectionEmpty: Bool {
        mode.selectedApps.applicationTokens.isEmpty &&
        mode.selectedApps.categoryTokens.isEmpty &&
        mode.selectedApps.webDomainTokens.isEmpty
    }
}
