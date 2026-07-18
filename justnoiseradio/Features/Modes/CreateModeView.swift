// CreateModeView.swift

import SwiftUI
import FamilyControls

struct CreateModeView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Binding var isPresented: Bool
    @State private var modeName: String = ""
    @State private var selection = FamilyActivitySelection()

    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                // Form with sections
                Form {
                    // Mode Name Section
                    Section(header: Text("Name")) {
                        TextField("Enter mode name", text: $modeName)
                            .autocapitalization(.words)
                        Text("Please enter a name for your mode.")
                            .font(.caption)
                            .foregroundColor(.gray)
                    }
                    
                    // Apps Selection Section
                    Section(header: Text("Select Apps to Block")) {
                        NavigationLink(destination: FamilyActivityPicker(selection: $selection)) {
                            HStack {
                                Image(systemName: "rectangle.stack")
                                Text("Choose Apps")
                                    .foregroundColor(.primary)
                                Spacer()
                                Text("\(selectedAppsCount) Apps, \(selectedCategoriesCount) Categories Selected")
                                    .foregroundColor(.gray)
                                    .font(.caption)
                            }
                        }
                    }
                }
                .navigationTitle("New Mode")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    // Cancel button on the left side of the navigation bar
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Cancel") {
                            isPresented = false
                        }
                    }
                    // Save button on the right side of the navigation bar
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Save") {
                            let newMode = Mode(name: modeName, selectedApps: selection)
                            nfcViewModel.addMode(newMode)
                            isPresented = false
                        }
                        .disabled(isSaveDisabled)
                        // Only enable when mode name and apps are selected
                    }
                }
            }
        }
    }
    
    // Computed Property for Selected Apps Count
    private var selectedAppsCount: Int {
        selection.applicationTokens.count
    }
    
    // Computed Property for Selected Categories Count
    private var selectedCategoriesCount: Int {
        selection.categoryTokens.count
    }
    
    // Computed Property to Determine if Save Button Should be Disabled
    private var isSaveDisabled: Bool {
        modeName.trimmingCharacters(in: .whitespaces).isEmpty || (selection.applicationTokens.isEmpty && selection.categoryTokens.isEmpty)
    }
}
