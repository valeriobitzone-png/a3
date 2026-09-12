import AppKit
import ApplicationServices

@main
struct OverlayMain {
    static func main() {
        let app = NSApplication.shared
        app.setActivationPolicy(.accessory)
        let delegate = OverlayDelegate()
        app.delegate = delegate
        app.run()
    }
}

final class OverlayDelegate: NSObject, NSApplicationDelegate {
    let panel = NSPanel(
        contentRect: NSRect(x: 0, y: 0, width: 1280, height: 800),
        styleMask: [.borderless, .nonactivatingPanel],
        backing: .buffered,
        defer: false
    )
    let effect = NSVisualEffectView()
    var cards: [[String: Any]] = []
    var intent = "trova volo Roma-Milano domani"
    var ambient = "A3 attivo"
    var blurUnavailable = false
    var frontmost: String?
    var axTrusted = AXIsProcessTrusted()
    var chooseId: String?
    var axObserver: AXObserver?
    var dragStart: NSPoint?

    func applicationDidFinishLaunching(_ notification: Notification) {
        parseArgs()
        guard let screen = NSScreen.main else { return }
        panel.setFrame(screen.visibleFrame, display: true)
        panel.level = .floating
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = false
        panel.ignoresMouseEvents = false
        panel.isFloatingPanel = true
        panel.hidesOnDeactivate = false
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary]
        panel.title = "a3 overlay"
        panel.isMovableByWindowBackground = false

        effect.material = .fullScreenUI
        effect.blendingMode = .behindWindow
        effect.state = .active
        effect.frame = panel.contentView?.bounds ?? .zero
        effect.autoresizingMask = [.width, .height]
        panel.contentView = effect

        let stack = NSStackView()
        stack.orientation = .vertical
        stack.alignment = .centerX
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        effect.addSubview(stack)

        let pill = label(ambient, size: 14, bold: true)
        pill.wantsLayer = true
        pill.layer?.backgroundColor = NSColor(calibratedWhite: 0.05, alpha: 0.7).cgColor
        pill.layer?.cornerRadius = 14
        stack.addArrangedSubview(pill)

        if blurUnavailable {
            stack.addArrangedSubview(label("blur unavailable", size: 13, bold: false))
        }

        stack.addArrangedSubview(label(intent, size: 16, bold: true))
        if let frontmost {
            stack.addArrangedSubview(label("frontmost \(frontmost)", size: 12, bold: false))
        }
        if axTrusted {
            stack.addArrangedSubview(label("Accessibility consented", size: 12, bold: false))
            startAxObserver()
        } else {
            stack.addArrangedSubview(label("Accessibility optional: contextual surfaces require consent", size: 12, bold: false))
        }

        for card in cards {
            let button = NSButton()
            button.title = "\(card["title"] as? String ?? "")  \(card["price"] as? String ?? "")"
            button.bezelStyle = .regularSquare
            button.isBordered = false
            button.wantsLayer = true
            button.layer?.backgroundColor = NSColor(calibratedWhite: 1, alpha: 0.22).cgColor
            button.layer?.cornerRadius = 22
            button.layer?.borderWidth = 1
            button.layer?.borderColor = NSColor(calibratedWhite: 1, alpha: 0.35).cgColor
            button.contentTintColor = .white
            button.font = NSFont.systemFont(ofSize: 15, weight: .semibold)
            button.target = self
            button.action = #selector(openCard(_:))
            button.identifier = NSUserInterfaceItemIdentifier(card["id"] as? String ?? "")
            button.toolTip = card["url"] as? String
            stack.addArrangedSubview(button)
            button.widthAnchor.constraint(equalToConstant: 640).isActive = true
            button.heightAnchor.constraint(equalToConstant: 88).isActive = true
        }

        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: effect.centerXAnchor),
            stack.topAnchor.constraint(equalTo: effect.topAnchor, constant: 48),
            stack.widthAnchor.constraint(lessThanOrEqualToConstant: 720)
        ])

        let pan = NSPanGestureRecognizer(target: self, action: #selector(swipeDown(_:)))
        effect.addGestureRecognizer(pan)

        panel.makeKeyAndOrderFront(nil)
        NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            if event.modifierFlags.contains(.command) && event.charactersIgnoringModifiers == "w" {
                NSApp.terminate(nil)
                return nil
            }
            return event
        }

        if let chooseId {
            openUrl(for: chooseId)
        }

        if let path = ProcessInfo.processInfo.environment["A3_OVERLAY_SHOT"] {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) {
                self.saveShot(path)
            }
        }

        if let seconds = ProcessInfo.processInfo.environment["A3_OVERLAY_HOLD"] {
            DispatchQueue.main.asyncAfter(deadline: .now() + (Double(seconds) ?? 2)) {
                NSApp.terminate(nil)
            }
        }
    }

    private func saveShot(_ path: String) {
        guard let view = panel.contentView else { return }
        let bounds = view.bounds
        guard let rep = view.bitmapImageRepForCachingDisplay(in: bounds) else { return }
        view.cacheDisplay(in: bounds, to: rep)
        try? rep.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: path), options: .atomic)
    }

    @objc func openCard(_ sender: NSButton) {
        openUrl(for: sender.identifier?.rawValue ?? "")
    }

    @objc func swipeDown(_ gesture: NSPanGestureRecognizer) {
        if gesture.state == .ended && gesture.translation(in: effect).y < -80 {
            NSApp.terminate(nil)
        }
    }

    private func openUrl(for id: String) {
        let url = cards.first { ($0["id"] as? String) == id }.flatMap { $0["url"] as? String }
            ?? cards.first.flatMap { $0["url"] as? String }
        if let url, let parsed = URL(string: url) {
            NSWorkspace.shared.open(parsed)
        }
    }

    private func startAxObserver() {
        guard let pid = NSWorkspace.shared.frontmostApplication?.processIdentifier else { return }
        var observer: AXObserver?
        let callback: AXObserverCallback = { _, _, _, _ in }
        guard AXObserverCreate(pid, callback, &observer) == .success, let observer else { return }
        let app = AXUIElementCreateApplication(pid)
        AXObserverAddNotification(observer, app, kAXFocusedWindowChangedNotification as CFString, nil)
        CFRunLoopAddSource(CFRunLoopGetMain(), AXObserverGetRunLoopSource(observer), .defaultMode)
        axObserver = observer
    }

    private func parseArgs() {
        let args = ProcessInfo.processInfo.arguments
        if let idx = args.firstIndex(of: "--json"), idx + 1 < args.count {
            let path = args[idx + 1]
            if let data = FileManager.default.contents(atPath: path),
               let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                intent = obj["intent"] as? String ?? intent
                ambient = obj["ambient"] as? String ?? ambient
                blurUnavailable = obj["blur_unavailable"] as? Bool ?? false
                frontmost = obj["frontmost"] as? String
                cards = obj["cards"] as? [[String: Any]] ?? []
            }
        }
        if let idx = args.firstIndex(of: "--choose"), idx + 1 < args.count {
            chooseId = args[idx + 1]
        }
        if cards.isEmpty {
            cards = [
                ["id": "transport.rail", "title": "Frecciarossa Roma–Milano 06:00–08:55", "price": "49.90 EUR", "url": "https://www.google.com/search?q=Frecciarossa%20Roma%20Milano%20domani"],
                ["id": "transport.air", "title": "Volo FCO–LIN 07:10–08:25", "price": "86.00 EUR", "url": "https://www.google.com/travel/flights?q=Rome%20to%20Milan%20tomorrow"],
                ["id": "transport.car", "title": "Auto A1 Roma–Milano ~5h20", "price": "fuel+toll ~45 EUR", "url": "https://www.google.com/maps/dir/Rome/Milan"]
            ]
        }
        if let app = NSWorkspace.shared.frontmostApplication {
            frontmost = frontmost ?? app.localizedName
        }
    }

    private func label(_ text: String, size: CGFloat, bold: Bool) -> NSTextField {
        let field = NSTextField(labelWithString: text)
        field.font = NSFont.systemFont(ofSize: size, weight: bold ? .semibold : .regular)
        field.textColor = .white
        field.alignment = .center
        field.drawsBackground = false
        field.isBezeled = false
        return field
    }
}
