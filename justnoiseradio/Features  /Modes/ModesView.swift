import SwiftUI
import FamilyControls

struct ModesView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Environment(\.presentationMode) var presentationMode
    @State private var temporarySelectedModeIndex: Int = 0
    @State private var isPresentingFamilyPicker = false
    @State private var isCreatingNewMode = false
    @State private var isPresentingEditMode = false

    var body: some View {
        NavigationView {
            VStack(spacing: 20) {

                // Title — full-screen spacing (no medium-sheet hacks)
                Text("What do you want to focus on?")
                    .font(.title)
                    .bold()
                    .multilineTextAlignment(.center)
                    .padding(.top, 40)

                Spacer(minLength: 8)

                // Mode Picker with edit button overlay
                ZStack(alignment: .trailing) {
                    Picker("Select Mode", selection: $temporarySelectedModeIndex) {
                        ForEach(0..<nfcViewModel.modes.count, id: \.self) { index in
                            Text(nfcViewModel.modes[index].name).tag(index)
                        }
                    }
                    .pickerStyle(WheelPickerStyle())
                    .labelsHidden()
                    .frame(height: 200)
                    .padding(.horizontal)

                    Button(action: { isPresentingEditMode = true }) {
                        Image(systemName: "pencil")
                            .foregroundColor(.blue)
                            .padding(10)
                    }
                    .padding(.trailing, 30)
                }
                .sheet(isPresented: $isPresentingEditMode) {
                    if temporarySelectedModeIndex < nfcViewModel.modes.count {
                        EditModeView(mode: $nfcViewModel.modes[temporarySelectedModeIndex])
                            .environmentObject(nfcViewModel)
                    }
                }

                // Subtle + New Mode
                Button(action: { isCreatingNewMode = true }) {
                    Text("+ New Mode")
                        .font(.subheadline)
                        .foregroundColor(.blue)
                        .padding(.top, 6)
                }
                .sheet(isPresented: $isCreatingNewMode) {
                    CreateModeView(isPresented: $isCreatingNewMode)
                        .environmentObject(nfcViewModel)
                }

                Spacer()

                // CTA — inside stack (works great full-screen, still OK in sheet)
                Button(action: { isPresentingFamilyPicker = true }) {
                    Text("\(blockedAppsCount) Apps, \(blockedCategoriesCount) Categories Blocked")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color.black)
                        .cornerRadius(50)
                        .padding(.horizontal)
                }
                .padding(.bottom, 20)
                .sheet(isPresented: $isPresentingFamilyPicker) {
                    if temporarySelectedModeIndex < nfcViewModel.modes.count {
                        FamilyActivityPickerView(
                            isPresented: $isPresentingFamilyPicker,
                            mode: $nfcViewModel.modes[temporarySelectedModeIndex]
                        )
                        .environmentObject(nfcViewModel)
                    }
                }
            }
            .navigationTitle("Select Modes")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") {
                        if temporarySelectedModeIndex < nfcViewModel.modes.count {
                            nfcViewModel.selectedMode = nfcViewModel.modes[temporarySelectedModeIndex]
                            nfcViewModel.saveSelectedMode()
                        }
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
            .onAppear {
                if let selectedMode = nfcViewModel.selectedMode,
                   let index = nfcViewModel.modes.firstIndex(where: { $0.id == selectedMode.id }) {
                    temporarySelectedModeIndex = index
                } else {
                    temporarySelectedModeIndex = 0
                    if !nfcViewModel.modes.isEmpty {
                        nfcViewModel.selectedMode = nfcViewModel.modes[0]
                    }
                }
            }
            .onDisappear {
                if temporarySelectedModeIndex < nfcViewModel.modes.count {
                    nfcViewModel.selectedMode = nfcViewModel.modes[temporarySelectedModeIndex]
                }
            }
        }
    }

    private var blockedAppsCount: Int {
        guard temporarySelectedModeIndex < nfcViewModel.modes.count else { return 0 }
        return nfcViewModel.modes[temporarySelectedModeIndex].selectedApps.applicationTokens.count
    }

    private var blockedCategoriesCount: Int {
        guard temporarySelectedModeIndex < nfcViewModel.modes.count else { return 0 }
        return nfcViewModel.modes[temporarySelectedModeIndex].selectedApps.categoryTokens.count
    }
}
