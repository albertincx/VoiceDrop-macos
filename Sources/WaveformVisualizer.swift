import SwiftUI

struct WaveformVisualizer: View {
    let levels: [Float]
    let isRecording: Bool

    var body: some View {
        HStack(spacing: 3) {
            ForEach(0..<levels.count, id: \.self) { index in
                RoundedRectangle(cornerRadius: 2)
                    .fill(
                        LinearGradient(
                            colors: isRecording ? [Color.red, Color.orange] : [Color.gray.opacity(0.3), Color.gray.opacity(0.5)],
                            startPoint: .bottom,
                            endPoint: .top
                        )
                    )
                    .frame(
                        width: 4,
                        height: isRecording ? max(4, CGFloat(levels[index]) * 44) : 4
                    )
                    .animation(.easeOut(duration: 0.08), value: levels[index])
            }
        }
        .frame(height: 48)
        .padding(.horizontal, 8)
    }
}
