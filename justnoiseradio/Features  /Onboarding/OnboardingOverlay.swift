//
//  OnboardingOverlay.swift
//  justnoise
//
//  Created by TJ on 08.09.25.
//

import SwiftUI

struct OnboardingStep {
    let highlightRect: CGRect
    let title: String
    let description: String
}

struct OnboardingOverlay: View {
    let step: OnboardingStep
    let onNext: () -> Void
    let onSkip: () -> Void
    
    var body: some View {
        ZStack {
            // Dimmed background with a transparent circle
            Color.black.opacity(0.6)
                .mask(
                    Rectangle()
                        .overlay(
                            Circle()
                                .frame(width: step.highlightRect.width + 30,
                                       height: step.highlightRect.height + 30)
                                .offset(x: step.highlightRect.midX - step.highlightRect.width / 2,
                                        y: step.highlightRect.midY - step.highlightRect.height / 2)
                                .blendMode(.destinationOut)
                        )
                )
                .ignoresSafeArea()
            
            VStack {
                Spacer()
                VStack(spacing: 12) {
                    Text(step.title)
                        .font(.title).bold()
                        .foregroundColor(.white)
                    
                    Text(step.description)
                        .foregroundColor(.white.opacity(0.8))
                        .multilineTextAlignment(.center)
                    
                    HStack {
                        Button("Skip") { onSkip() }
                            .foregroundColor(.gray)
                        
                        Spacer()
                        
                        Button("Next") { onNext() }
                            .foregroundColor(.yellow)
                            .bold()
                    }
                }
                .padding()
                .background(Color.black.opacity(0.8))
                .cornerRadius(12)
                .padding()
            }
        }
        .compositingGroup()
    }
}
