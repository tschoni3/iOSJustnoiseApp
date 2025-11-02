// EditModeView.swift

import SwiftUI
import FamilyControls

struct EditModeView: View {
    @Environment(\.presentationMode) var presentationMode
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Binding var mode: Mode // The mode to edit
    @State private var editedName: String
    @State private var selection: FamilyActivitySelection
    @State private var showDeleteConfirmation = false // Controls delete confirmation alert
    
    init(mode: Binding<Mode>) {
        self._mode = mode
        self._editedName = State(initialValue: mode.wrappedValue.name)
        self._selection = State(initialValue: mode.wrappedValue.selectedApps)
    }
    
    var body: some View {
        NavigationView {
            VStack(spacing: 20) {
                Form {
                    // Mode Name Section
                    Section(header: Text("Name")) {
                        TextField("Enter mode name", text: $editedName)
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
                    
                    // Delete Mode Section
                    Section {
                        Button(action: {
                            showDeleteConfirmation = true
                        }) {
                            Text("Delete Mode")
                                .foregroundColor(.red)
                                .frame(maxWidth: .infinity, alignment: .center)
                        }
                    }
                }
                .navigationTitle("Edit Mode")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    // Cancel button on the left side of the navigation bar
                    ToolbarItem(placement: .navigationBarLeading) {
                        Button("Cancel") {
                            presentationMode.wrappedValue.dismiss()
                        }
                    }
                    // Save button on the right side of the navigation bar
                    ToolbarItem(placement: .navigationBarTrailing) {
                        Button("Save") {
                            saveChanges()
                        }
                        .disabled(isSaveDisabled)
                        // Only enable when mode name and apps are selected
                    }
                }
                .alert(isPresented: $showDeleteConfirmation) {
                    Alert(
                        title: Text("Delete Mode"),
                        message: Text("Are you sure you want to delete this mode? This action cannot be undone."),
                        primaryButton: .destructive(Text("Delete")) {
                            deleteMode()
                        },
                        secondaryButton: .cancel()
                    )
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
        editedName.trimmingCharacters(in: .whitespaces).isEmpty || (selection.applicationTokens.isEmpty && selection.categoryTokens.isEmpty)
    }
    
    // Save Changes to Mode
    private func saveChanges() {
        mode.name = editedName.trimmingCharacters(in: .whitespaces)
        mode.selectedApps = selection
        nfcViewModel.updateMode(mode) // Update mode in the view model
        presentationMode.wrappedValue.dismiss() // Close the screen
    }
    
    // Delete Mode from ViewModel
    private func deleteMode() {
        if let index = nfcViewModel.modes.firstIndex(where: { $0.id == mode.id }) {
            nfcViewModel.modes.remove(at: index)
            nfcViewModel.saveModes() // Save changes
            presentationMode.wrappedValue.dismiss() // Close the screen
        }
    }
}
