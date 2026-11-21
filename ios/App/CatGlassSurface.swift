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
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    var body: some View {
        surface.allowsHitTesting(false)
    }
    @ViewBuilder private var surface: some View {
        if reduceTransparency {
            shape.fill(Color(uiColor: .secondarySystemBackground))
        } else {
#if compiler(>=6.2)
            if #available(iOS 26.0, *) {
                Color.clear.glassEffect(.clear, in: shape)
            } else {
                fallback
            }
#else
            fallback
#endif
        }
    }
    private var fallback: some View {
        BackdropBlur()
            .overlay(Color(uiColor: .secondarySystemBackground).opacity(0.08))
            .clipShape(shape)
            .overlay(shape.stroke(LinearGradient(colors: [.white.opacity(colorScheme == .dark ? 0.35 : 0.8), .white.opacity(0.02), .white.opacity(0.24)], startPoint: .topLeading, endPoint: .bottomTrailing), lineWidth: 0.7))
            .shadow(color: .black.opacity(colorScheme == .dark ? 0.16 : 0.08), radius: 8, y: 4)
    }
}
