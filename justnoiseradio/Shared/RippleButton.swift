// RippleButton.swift

import SwiftUI

struct RippleButton: View {
    var action: () -> Void
    var isActive: Bool
    var activeColor: Color
    var inactiveColor: Color
    var logoImageName: String
    
    @State private var startAnimation: Bool = false
    @State private var random1: CGFloat = 0.5
    @State private var random2: CGFloat = 0.5
    
    // Timer for updating ripple sizes
    @State private var timer = Timer.publish(every: 0.3, on: .main, in: .common).autoconnect()
    
    func stopTimer() {
        timer.upstream.connect().cancel()
    }
    
    func startTimer() {
        timer = Timer.publish(every: 0.3, on: .main, in: .common).autoconnect()
    }
    
    var body: some View {
        ZStack {
            if isActive {
                // Enhanced Ripple Effect
                ZStack {
                    Circle()
                        .fill(RadialGradient(
                            gradient: Gradient(colors: [activeColor.opacity(0.3), Color.white.opacity(0.1)]),
                            center: .center,
                            startRadius: 0,
                            endRadius: 400))
                        .frame(width: random2 * 500, height: random2 * 500)
                    
                    Circle()
                        .fill(RadialGradient(
                            gradient: Gradient(colors: [Color.gray.opacity(0.3), Color.white.opacity(0.01)]),
                            center: .center,
                            startRadius: 0,
                            endRadius: 400))
                        .frame(width: random1 * 400, height: random1 * 400)
                    
                    Circle()
                        .fill(RadialGradient(
                            gradient: Gradient(colors: [activeColor.opacity(0.3), Color.white.opacity(0.1)]),
                            center: .center,
                            startRadius: 150,
                            endRadius: 190))
                        .frame(width: 200, height: 200)
                }
                .scaleEffect(startAnimation ? random1 : 1)
                .animation(.easeInOut, value: random1)
                .onAppear {
                    startAnimation = true
                    startTimer()
                }
                .onDisappear {
                    startAnimation = false
                    stopTimer()
                }
            }
            
            // Main Button with Justnoise Logo
            Button(action: {
                action()
                if isActive {
                    stopTimer()
                } else {
                    startTimer()
                }
            }) {
                Image(logoImageName)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: 120, height: 120)
                    .foregroundColor(isActive ? activeColor : inactiveColor)
                    .background(
                        Circle()
                            .fill(Color(red: 71/255, green: 71/255, blue: 71/255)) // #474747
                            .frame(width: 220, height: 220)
                    )
                    .shadow(color: isActive ? activeColor.opacity(0.7) : inactiveColor.opacity(0.7), radius: 10, x: 0, y: 0)
            }
            .buttonStyle(SolidButtonStyle()) // Apply the custom button style here
            .accessibilityLabel(isActive ? "Stop Recording" : "Start Recording")
            .accessibilityAddTraits(.isButton)
        }
        .onReceive(timer) { _ in
            random1 = CGFloat.random(in: 0.5...1)
            random2 = CGFloat.random(in: 0.5...1)
        }
        .onAppear {
            if isActive {
                startTimer()
            } else {
                stopTimer()
            }
        }
    }
}

struct RippleButton_Previews: PreviewProvider {
    static var previews: some View {
        RippleButton(
            action: {},
            isActive: true,
            activeColor: Color(red: 221/255, green: 255/255, blue: 0),
            inactiveColor: .white,
            logoImageName: "JustnoiseLogo"
        )
    }
}
