import SwiftUI

@MainActor
final class NavigationChrome: ObservableObject {
    @Published private var detailIDs: Set<UUID> = []
    var detailVisible: Bool { !detailIDs.isEmpty }
    func enter(_ id: UUID) { detailIDs.insert(id) }
    func leave(_ id: UUID) { detailIDs.remove(id) }
}
struct FloatingNavigation: View {
    @Binding var selection: Int
    let openLive: () -> Void
    @Namespace private var highlight
    private let titles = ["首页", "记录", "追剧", "我的"]
    private let icons = ["ic_tab_home", "ic_tab_records", "ic_tab_following", "ic_tab_mine"]
    private let identifiers = ["nav-home", "nav-records", "nav-following", "nav-settings"]
    var body: some View {
        HStack(spacing: 0) {
            tab(0)
            tab(1)
            Button(action: openLive) {
                Image("ic_live_fab").resizable().scaledToFit().frame(width: 24, height: 24).frame(maxWidth: .infinity).frame(height: 52).contentShape(Rectangle())
            }.buttonStyle(.plain).foregroundStyle(.primary).accessibilityLabel("直播").accessibilityIdentifier("nav-live")
            tab(2)
            tab(3)
        }.padding(6).frame(height: 64)
            .background { CatGlassSurface(shape: Capsule()) }
            .padding(.horizontal, 16).padding(.bottom, 12)
    }
    private func tab(_ index: Int) -> some View {
        Button {
            withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { selection = index }
        } label: {
            VStack(spacing: 1) {
                Image(icons[index]).resizable().scaledToFit().frame(width: 24, height: 24)
                if selection == index { Text(titles[index]).font(.system(size: 11, weight: .medium)).lineLimit(1) }
            }.frame(maxWidth: .infinity).frame(height: 52).contentShape(Rectangle())
                .background {
                    if selection == index { Capsule().fill(Color.accentColor.opacity(0.12)).matchedGeometryEffect(id: "selected", in: highlight) }
                }
        }.buttonStyle(.plain)
            .foregroundStyle(selection == index ? Color.accentColor : Color.primary.opacity(0.75))
            .accessibilityLabel(titles[index]).accessibilityValue(selection == index ? "已选中" : "")
            .accessibilityIdentifier(identifiers[index])
    }
}
struct CatalogChipStyle: ButtonStyle {
    let selected: Bool
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.system(size: 14, weight: .medium))
            .padding(.horizontal, 14).padding(.vertical, 7)
            .foregroundStyle(selected ? Color.accentColor : Color.primary)
            .background(selected ? Color.accentColor.opacity(0.12) : Color.secondary.opacity(0.07), in: Capsule())
            .opacity(configuration.isPressed ? 0.65 : 1)
    }
}
