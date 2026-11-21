import SwiftUI
import UIKit

private struct BackdropBlur: UIViewRepresentable {
    func makeUIView(context: Context) -> UIVisualEffectView {
        let view = UIVisualEffectView(effect: UIBlurEffect(style: .systemUltraThinMaterial))
        view.isUserInteractionEnabled = false
        return view
    }
    func updateUIView(_ view: UIVisualEffectView, context: Context) {}
}
struct CatGlassSurface<S: Shape>: View {
    let shape: S
    @Environment(\.colorScheme) private var colorScheme
    var body: some View {
        BackdropBlur()
            .overlay(Color(uiColor: .secondarySystemBackground).opacity(0.4))
            .clipShape(shape)
            .overlay(shape.stroke(LinearGradient(colors: [.white.opacity(colorScheme == .dark ? 0.22 : 0.7), .white.opacity(0.05), .black.opacity(0.08)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 0.7))
            .shadow(color: .black.opacity(colorScheme == .dark ? 0.2 : 0.1), radius: 8, y: 4)
            .allowsHitTesting(false)
    }
}
