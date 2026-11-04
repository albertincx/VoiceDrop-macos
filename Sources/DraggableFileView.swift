import SwiftUI
import AppKit

struct DraggableAudioCard: View {
    let fileURL: URL
    var mp3URL: URL? = nil
    let onCopyPath: () -> Void
    var onCopyMP3: (() -> Void)? = nil

    @State private var isHovering: Bool = false
    @State private var showCopiedBadge: Bool = false
    @State private var showCopiedMP3Badge: Bool = false

    var fileSizeString: String {
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: fileURL.path),
              let size = attrs[.size] as? Int64 else { return "0 KB" }
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useKB, .useMB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: size)
    }

    var body: some View {
        HStack(spacing: 14) {
            // Drag handle with icon
            ZStack {
                RoundedRectangle(cornerRadius: 12)
                    .fill(LinearGradient(colors: [Color.blue.opacity(0.8), Color.purple.opacity(0.8)], startPoint: .topLeading, endPoint: .bottomTrailing))
                    .frame(width: 48, height: 48)
                    .shadow(color: Color.blue.opacity(0.3), radius: 6, x: 0, y: 3)

                Image(systemName: "waveform.badge.mic")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundColor(.white)
            }

            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(fileURL.lastPathComponent)
                        .font(.system(size: 13, weight: .semibold))
                        .lineLimit(1)
                        .truncationMode(.middle)

                    Spacer()

                    Text(fileSizeString)
                        .font(.system(size: 11, weight: .regular))
                        .foregroundColor(.secondary)
                }

                HStack(spacing: 6) {
                    Image(systemName: "hand.draw.fill")
                        .font(.system(size: 11))
                        .foregroundColor(.accentColor)

                    Text("Потяните отсюда мышкой в Antigravity или Finder")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.accentColor)
                }
            }

            Spacer()

            // Quick Actions: Copy MP3, Copy WAV File & Reveal in Finder
            HStack(spacing: 8) {
                if let onCopyMP3 = onCopyMP3 {
                    Button(action: {
                        onCopyMP3()
                        withAnimation {
                            showCopiedMP3Badge = true
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                            withAnimation {
                                showCopiedMP3Badge = false
                            }
                        }
                    }) {
                        HStack(spacing: 3) {
                            Image(systemName: showCopiedMP3Badge ? "checkmark.circle.fill" : "music.note")
                                .foregroundColor(showCopiedMP3Badge ? .green : .orange)
                            Text(showCopiedMP3Badge ? "Скопирован!" : "MP3")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(showCopiedMP3Badge ? .green : .orange)
                        }
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Color.orange.opacity(0.12))
                        .cornerRadius(6)
                    }
                    .buttonStyle(.plain)
                    .help("Скопировать запись как MP3 в буфер обмена")
                }

                Button(action: {
                    copyFileToClipboard()
                }) {
                    Image(systemName: showCopiedBadge ? "checkmark.circle.fill" : "doc.on.doc")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(showCopiedBadge ? .green : .primary)
                }
                .buttonStyle(.plain)
                .help("Скопировать WAV в буфер обмена (Cmd+C)")

                Button(action: {
                    NSWorkspace.shared.activateFileViewerSelecting([fileURL])
                }) {
                    Image(systemName: "folder")
                        .font(.system(size: 13, weight: .medium))
                }
                .buttonStyle(.plain)
                .help("Показать в Finder")
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 14)
                .fill(isHovering ? Color(NSColor.controlBackgroundColor) : Color(NSColor.controlBackgroundColor).opacity(0.7))
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(isHovering ? Color.accentColor.opacity(0.6) : Color.gray.opacity(0.2), lineWidth: isHovering ? 1.5 : 1)
                )
        )
        .overlay(
            // Underlying native AppKit drag provider for rock-solid Drag & Drop to Electron (Antigravity)
            NativeDraggableViewRepresentable(fileURL: fileURL)
                .opacity(0.01)
        )
        // SwiftUI standard drag as secondary layer
        .onDrag {
            let provider = NSItemProvider(object: fileURL as NSURL)
            provider.suggestedName = fileURL.lastPathComponent
            return provider
        }
        .onHover { hover in
            withAnimation(.easeInOut(duration: 0.15)) {
                self.isHovering = hover
            }
        }
    }

    private func copyFileToClipboard() {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.writeObjects([fileURL as NSURL])
        withAnimation {
            showCopiedBadge = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            withAnimation {
                showCopiedBadge = false
            }
        }
    }
}

// Native AppKit view providing NSDraggingSource for universal compatibility
struct NativeDraggableViewRepresentable: NSViewRepresentable {
    let fileURL: URL

    func makeNSView(context: Context) -> AppKitDraggableOverlay {
        let view = AppKitDraggableOverlay()
        view.fileURL = fileURL
        return view
    }

    func updateNSView(_ nsView: AppKitDraggableOverlay, context: Context) {
        nsView.fileURL = fileURL
    }
}

class AppKitDraggableOverlay: NSView, NSDraggingSource {
    var fileURL: URL?

    override init(frame frameRect: NSRect) {
        super.init(frame: frameRect)
        registerForDraggedTypes([.fileURL])
    }

    required init?(coder: NSCoder) {
        super.init(coder: coder)
        registerForDraggedTypes([.fileURL])
    }

    override func mouseDragged(with event: NSEvent) {
        guard let fileURL = fileURL else { return }

        // Create standard pasteboard item writer
        let draggingItem = NSDraggingItem(pasteboardWriter: fileURL as NSURL)

        let icon = NSWorkspace.shared.icon(forFile: fileURL.path)
        icon.size = NSSize(width: 48, height: 48)
        let dragRect = NSRect(x: max(0, bounds.midX - 24), y: max(0, bounds.midY - 24), width: 48, height: 48)
        draggingItem.setDraggingFrame(dragRect, contents: icon)

        let session = beginDraggingSession(with: [draggingItem], event: event, source: self)
        session.animatesToStartingPositionsOnCancelOrFail = true
    }

    func draggingSession(_ session: NSDraggingSession, sourceOperationMaskFor context: NSDraggingContext) -> NSDragOperation {
        return .copy
    }
}
