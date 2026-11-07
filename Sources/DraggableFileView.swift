import SwiftUI
import AppKit

struct DraggableAudioCard: View {
    @ObservedObject private var loc = Localization.shared
    let fileURL: URL
    var mp3URL: URL? = nil
    let onCopyPath: () -> Void
    var onCopyMP3: (() -> Void)? = nil
    var onCopyWAV: (() -> Void)? = nil

    @State private var isHovering: Bool = false
    @State private var showCopiedWAVBadge: Bool = false
    @State private var showCopiedMP3Badge: Bool = false

    var resolvedMP3URL: URL? {
        if let mp3 = mp3URL, FileManager.default.fileExists(atPath: mp3.path) {
            return mp3
        }
        let candidate = fileURL.deletingPathExtension().appendingPathExtension("mp3")
        if FileManager.default.fileExists(atPath: candidate.path) {
            return candidate
        }
        return nil
    }

    var effectiveDragURL: URL {
        return resolvedMP3URL ?? fileURL
    }

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

                    Text(loc.t(.dragHint))
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.accentColor)
                }
            }

            Spacer()

            // Quick Actions: Default Copy (MP3), Separate Copy WAV & Reveal in Finder
            HStack(spacing: 8) {
                // Default Copy Button: Copies MP3 format
                Button(action: {
                    if let onCopyMP3 = onCopyMP3 {
                        onCopyMP3()
                    } else {
                        copyMP3ToClipboard()
                    }
                    withAnimation {
                        showCopiedMP3Badge = true
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        withAnimation {
                            showCopiedMP3Badge = false
                        }
                    }
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: showCopiedMP3Badge ? "checkmark.circle.fill" : "doc.on.doc")
                            .foregroundColor(showCopiedMP3Badge ? .green : .orange)
                        Text(showCopiedMP3Badge ? loc.t(.copiedMP3Badge) : "MP3")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(showCopiedMP3Badge ? .green : .orange)
                    }
                    .padding(.horizontal, 7)
                    .padding(.vertical, 4)
                    .background(Color.orange.opacity(0.14))
                    .cornerRadius(6)
                }
                .buttonStyle(.plain)
                .help(loc.t(.copyMP3Help))

                // Separate Button for WAV format
                Button(action: {
                    if let onCopyWAV = onCopyWAV {
                        onCopyWAV()
                    } else {
                        copyWAVToClipboard()
                    }
                    withAnimation {
                        showCopiedWAVBadge = true
                    }
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                        withAnimation {
                            showCopiedWAVBadge = false
                        }
                    }
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: showCopiedWAVBadge ? "checkmark.circle.fill" : "waveform")
                            .foregroundColor(showCopiedWAVBadge ? .green : .blue)
                        Text(showCopiedWAVBadge ? loc.t(.copiedWAVBadge) : "WAV")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(showCopiedWAVBadge ? .green : .blue)
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 4)
                    .background(Color.blue.opacity(0.12))
                    .cornerRadius(6)
                }
                .buttonStyle(.plain)
                .help(loc.t(.copyWavHelp))

                // Reveal in Finder
                Button(action: {
                    NSWorkspace.shared.activateFileViewerSelecting([fileURL])
                }) {
                    Image(systemName: "folder")
                        .font(.system(size: 13, weight: .medium))
                }
                .buttonStyle(.plain)
                .help(loc.t(.showInFinderHelp))
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
            NativeDraggableViewRepresentable(fileURL: effectiveDragURL)
                .opacity(0.01)
        )
        // SwiftUI standard drag as secondary layer
        .onDrag {
            let provider = NSItemProvider(object: effectiveDragURL as NSURL)
            provider.suggestedName = effectiveDragURL.lastPathComponent
            return provider
        }
        .onHover { hover in
            withAnimation(.easeInOut(duration: 0.15)) {
                self.isHovering = hover
            }
        }
    }

    private func copyWAVToClipboard() {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.writeObjects([fileURL as NSURL])
    }

    private func copyMP3ToClipboard() {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        if let mp3 = resolvedMP3URL {
            pasteboard.writeObjects([mp3 as NSURL])
        } else {
            pasteboard.writeObjects([fileURL as NSURL])
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
