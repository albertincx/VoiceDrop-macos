import Foundation
import AVFoundation
import Combine

class AudioPlayer: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published var isPlaying: Bool = false
    @Published var currentTime: TimeInterval = 0.0
    @Published var duration: TimeInterval = 0.0
    @Published var currentURL: URL?
    @Published var playbackError: String?

    private var player: AVAudioPlayer?
    private var timer: Timer?
    private var tempPlaybackURL: URL?

    deinit {
        cleanupTempPlaybackFile()
    }

    private func cleanupTempPlaybackFile() {
        if let temp = tempPlaybackURL {
            try? FileManager.default.removeItem(at: temp)
            tempPlaybackURL = nil
        }
    }

    private func decodeOGGToTempWAV(url: URL) -> URL? {
        let ffmpegPaths = ["/opt/homebrew/bin/ffmpeg", "/usr/local/bin/ffmpeg"]
        guard let ffmpeg = ffmpegPaths.first(where: { FileManager.default.isExecutableFile(atPath: $0) }) else {
            return nil
        }
        let tempURL = FileManager.default.temporaryDirectory
            .appendingPathComponent("voicedrop_play_\(UUID().uuidString).wav")
        let process = Process()
        process.executableURL = URL(fileURLWithPath: ffmpeg)
        process.arguments = ["-y", "-i", url.path, "-acodec", "pcm_s16le", tempURL.path]
        process.standardOutput = FileHandle.nullDevice
        process.standardError = FileHandle.nullDevice
        do {
            try process.run()
            process.waitUntilExit()
            if process.terminationStatus == 0, FileManager.default.fileExists(atPath: tempURL.path) {
                return tempURL
            }
        } catch {
            print("AudioPlayer ffmpeg decode failed: \(error)")
        }
        try? FileManager.default.removeItem(at: tempURL)
        return nil
    }

    func loadAudio(url: URL) {
        stop()
        cleanupTempPlaybackFile()
        currentURL = url
        playbackError = nil

        guard FileManager.default.fileExists(atPath: url.path) else {
            playbackError = "Файл не найден на диске"
            duration = 0.0
            return
        }

        var playableURL = url
        if url.pathExtension.lowercased() == "ogg" {
            if let decoded = decodeOGGToTempWAV(url: url) {
                tempPlaybackURL = decoded
                playableURL = decoded
            }
        }

        do {
            let p = try AVAudioPlayer(contentsOf: playableURL)
            p.delegate = self
            p.volume = 1.0
            let prepared = p.prepareToPlay()
            self.player = p
            self.duration = p.duration
            self.currentTime = 0.0
            print("AudioPlayer loaded: \(url.lastPathComponent), duration: \(p.duration), prepared: \(prepared)")
        } catch {
            self.playbackError = "Ошибка загрузки: \(error.localizedDescription)"
            print("AudioPlayer failed to load: \(error)")
        }
    }

    func togglePlayPause() {
        if isPlaying {
            pause()
        } else {
            play()
        }
    }

    func play() {
        if player == nil, let url = currentURL {
            loadAudio(url: url)
        }

        guard let player = player else {
            if let url = currentURL { loadAudio(url: url) }
            return
        }

        // Rewind if at end
        if player.currentTime >= player.duration || (player.duration > 0 && player.currentTime >= player.duration - 0.05) {
            player.currentTime = 0.0
            self.currentTime = 0.0
        }

        player.volume = 1.0
        let success = player.play()
        if success {
            isPlaying = true
            startTimer()
        } else {
            playbackError = "Не удалось начать воспроизведение"
        }
    }

    func pause() {
        player?.pause()
        isPlaying = false
        stopTimer()
    }

    func stop() {
        player?.stop()
        isPlaying = false
        stopTimer()
        currentTime = 0.0
    }

    func reset() {
        stop()
        cleanupTempPlaybackFile()
        player = nil
        currentURL = nil
        duration = 0.0
        playbackError = nil
    }

    func seek(to time: TimeInterval) {
        player?.currentTime = time
        currentTime = time
    }

    private func startTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 0.05, repeats: true) { [weak self] _ in
            guard let self = self, let player = self.player else { return }
            self.currentTime = player.currentTime
        }
    }

    private func stopTimer() {
        timer?.invalidate()
        timer = nil
    }

    func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        DispatchQueue.main.async {
            self.isPlaying = false
            self.currentTime = 0.0
            self.stopTimer()
            player.currentTime = 0.0
        }
    }
}
