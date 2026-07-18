import SwiftUI
import FamilyControls

struct ModesView: View {
    @EnvironmentObject var nfcViewModel: NFCViewModel
    @Environment(\.presentationMode) var presentationMode

    @State private var temporarySelectedModeIndex: Int = 0
    @State private var isPresentingFamilyPicker = false
    @State private var isCreatingNewMode = false
    @State private var isPresentingEditMode = false

    // Controls how much vertical area is reserved for:
    // New Mode -> Info Card -> CTA
    private let bottomBlockHeight: CGFloat = 220

    var body: some View {
        NavigationView {
            VStack(spacing: 20) {

                // Title — max width ~80%, allow 2 lines
                Text("What do you want to make space for?")
                    .font(.title)
                    .bold()
                    .multilineTextAlignment(.center)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: UIScreen.main.bounds.width * 0.8)
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

                // ✅ Distributed spacing block (New Mode -> Info Card -> CTA)
                bottomDistributedBlock()
                    .frame(height: bottomBlockHeight)
                    .padding(.bottom, 20)
            }
            .navigationTitle("Select Mode")
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
            .onChange(of: temporarySelectedModeIndex) { _, newIndex in
                if newIndex < nfcViewModel.modes.count {
                    nfcViewModel.selectedMode = nfcViewModel.modes[newIndex]
                }
            }
            .onDisappear {
                if temporarySelectedModeIndex < nfcViewModel.modes.count {
                    nfcViewModel.selectedMode = nfcViewModel.modes[temporarySelectedModeIndex]
                }
            }
        }
    }

    // MARK: - Distributed Bottom Block

    @ViewBuilder
    private func bottomDistributedBlock() -> some View {
        VStack {
            // New Mode Button
            Button(action: { isCreatingNewMode = true }) {
                Text("+ New Mode")
                    .font(.subheadline)
                    .foregroundColor(.blue)
            }
            .sheet(isPresented: $isCreatingNewMode) {
                CreateModeView(isPresented: $isCreatingNewMode)
                    .environmentObject(nfcViewModel)
            }

            Spacer(minLength: 0)

            // Info Card
            blockedInfoCard()

            Spacer(minLength: 0)

            // CTA Button
            Button(action: { isPresentingFamilyPicker = true }) {
                Text("Select apps to block")
                    .font(.headline)
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding()
                    .background(Color.black)
                    .cornerRadius(50)
                    .padding(.horizontal)
            }
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
    }

    // MARK: - Info Card

    @ViewBuilder
    private func blockedInfoCard() -> some View {
        VStack(spacing: 10) {
            Text("Blocked in this mode")
                .font(.footnote)
                .foregroundColor(.secondary)

            HStack(spacing: 14) {
                VStack(spacing: 2) {
                    Text("\(blockedAppsCount)")
                        .font(.system(size: 18, weight: .semibold))
                    Text(blockedAppsCount == 1 ? "App" : "Apps")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }

                Divider()
                    .frame(height: 26)
                    .opacity(0.35)

                VStack(spacing: 2) {
                    Text("\(blockedCategoriesCount)")
                        .font(.system(size: 18, weight: .semibold))
                    Text(blockedCategoriesCount == 1 ? "Category" : "Categories")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            .frame(maxWidth: .infinity, alignment: .center)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 14)
        .background(Color.primary.opacity(0.05))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color.primary.opacity(0.06), lineWidth: 1)
        )
        .cornerRadius(14)
        .fixedSize(horizontal: true, vertical: false)
        .frame(maxWidth: .infinity, alignment: .center)
    }

    // MARK: - Counts

    private var blockedAppsCount: Int {
        guard temporarySelectedModeIndex < nfcViewModel.modes.count else { return 0 }
        return nfcViewModel.modes[temporarySelectedModeIndex].selectedApps.applicationTokens.count
    }

    private var blockedCategoriesCount: Int {
        guard temporarySelectedModeIndex < nfcViewModel.modes.count else { return 0 }
        return nfcViewModel.modes[temporarySelectedModeIndex].selectedApps.categoryTokens.count
    }
}

#if DEBUG
struct ModesView_Previews: PreviewProvider {

    static var previewVM: NFCViewModel = {
        let vm = NFCViewModel()
        if vm.selectedMode == nil {
            vm.selectedMode = vm.modes.first
        }
        return vm
    }()

    static var previews: some View {
        Group {
            ModesView()
                .environmentObject(previewVM)
                .preferredColorScheme(.light)

            ModesView()
                .environmentObject(previewVM)
                .preferredColorScheme(.dark)
        }
    }
}
#endif
