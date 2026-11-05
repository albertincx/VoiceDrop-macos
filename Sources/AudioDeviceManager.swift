import Foundation
import CoreAudio
import Combine

enum DeviceTransportType {
    case usb
    case builtIn
    case bluetooth
    case other

    func localized(for lang: AppLanguage) -> String {
        switch self {
        case .usb:
            return lang == .en ? "Wired USB" : "Проводной USB"
        case .builtIn:
            return lang == .en ? "Built-in" : "Встроенный"
        case .bluetooth:
            return "Bluetooth"
        case .other:
            return lang == .en ? "Audio Device" : "Аудиоустройство"
        }
    }
}

struct AudioInputDevice: Identifiable, Hashable {
    let id: AudioDeviceID
    let name: String
    let isWired: Bool
    let transportType: DeviceTransportType

    func localizedDisplayName(for lang: AppLanguage) -> String {
        let icon = isWired ? "🎧" : (transportType == .bluetooth ? "📶" : "💻")
        return "\(icon) \(name) (\(transportType.localized(for: lang)))"
    }

    var displayName: String {
        return localizedDisplayName(for: Localization.shared.currentLanguage)
    }
}

class AudioDeviceManager: ObservableObject {
    @Published var availableDevices: [AudioInputDevice] = []
    @Published var selectedDeviceID: AudioDeviceID = 0

    init() {
        refreshDevices()
    }

    func refreshDevices() {
        var propertyAddress = AudioObjectPropertyAddress(
            mSelector: kAudioHardwarePropertyDevices,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )

        var dataSize: UInt32 = 0
        AudioObjectGetPropertyDataSize(AudioObjectID(kAudioObjectSystemObject), &propertyAddress, 0, nil, &dataSize)

        let deviceCount = Int(dataSize) / MemoryLayout<AudioDeviceID>.size
        guard deviceCount > 0 else { return }

        var deviceIDs = [AudioDeviceID](repeating: 0, count: deviceCount)
        AudioObjectGetPropertyData(AudioObjectID(kAudioObjectSystemObject), &propertyAddress, 0, nil, &dataSize, &deviceIDs)

        var devices: [AudioInputDevice] = []

        for id in deviceIDs {
            // Check input streams
            var streamAddress = AudioObjectPropertyAddress(
                mSelector: kAudioDevicePropertyStreams,
                mScope: kAudioObjectPropertyScopeInput,
                mElement: kAudioObjectPropertyElementMain
            )
            var streamSize: UInt32 = 0
            AudioObjectGetPropertyDataSize(id, &streamAddress, 0, nil, &streamSize)
            guard streamSize > 0 else { continue }

            // Get device name
            var nameAddress = AudioObjectPropertyAddress(
                mSelector: kAudioDevicePropertyDeviceNameCFString,
                mScope: kAudioObjectPropertyScopeGlobal,
                mElement: kAudioObjectPropertyElementMain
            )
            var nameCF: CFString = "" as CFString
            var nameSize = UInt32(MemoryLayout<CFString>.size)
            let _ = withUnsafeMutablePointer(to: &nameCF) { ptr in
                AudioObjectGetPropertyData(id, &nameAddress, 0, nil, &nameSize, ptr)
            }
            let name = nameCF as String

            // Transport type
            var transportAddress = AudioObjectPropertyAddress(
                mSelector: kAudioDevicePropertyTransportType,
                mScope: kAudioObjectPropertyScopeGlobal,
                mElement: kAudioObjectPropertyElementMain
            )
            var transport: UInt32 = 0
            var tSize = UInt32(MemoryLayout<UInt32>.size)
            AudioObjectGetPropertyData(id, &transportAddress, 0, nil, &tSize, &transport)

            let isUsb = (transport == 1970496032) // 'usb '
            let isBuiltIn = (transport == 1651274862) // 'bltn'
            let isBluetooth = (transport == 1651808629) // 'blue'

            let transportType: DeviceTransportType
            if isUsb { transportType = .usb }
            else if isBuiltIn { transportType = .builtIn }
            else if isBluetooth { transportType = .bluetooth }
            else { transportType = .other }

            devices.append(AudioInputDevice(id: id, name: name, isWired: isUsb, transportType: transportType))
        }

        // Current default input
        var defAddress = AudioObjectPropertyAddress(
            mSelector: kAudioHardwarePropertyDefaultInputDevice,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        var defaultID: AudioDeviceID = 0
        var defSize = UInt32(MemoryLayout<AudioDeviceID>.size)
        AudioObjectGetPropertyData(AudioObjectID(kAudioObjectSystemObject), &defAddress, 0, nil, &defSize, &defaultID)

        DispatchQueue.main.async {
            self.availableDevices = devices

            // Prefer wired microphone (USB) automatically if available
            if let wired = devices.first(where: { $0.isWired }) {
                self.selectedDeviceID = wired.id
                self.selectDevice(id: wired.id)
            } else if devices.contains(where: { $0.id == defaultID }) {
                self.selectedDeviceID = defaultID
            } else if let first = devices.first {
                self.selectedDeviceID = first.id
                self.selectDevice(id: first.id)
            }
        }
    }

    func selectDevice(id: AudioDeviceID) {
        var deviceID = id
        var propertyAddress = AudioObjectPropertyAddress(
            mSelector: kAudioHardwarePropertyDefaultInputDevice,
            mScope: kAudioObjectPropertyScopeGlobal,
            mElement: kAudioObjectPropertyElementMain
        )
        let size = UInt32(MemoryLayout<AudioDeviceID>.size)
        let status = AudioObjectSetPropertyData(AudioObjectID(kAudioObjectSystemObject), &propertyAddress, 0, nil, size, &deviceID)
        if status == noErr {
            DispatchQueue.main.async {
                self.selectedDeviceID = id
            }
        }
    }
}
