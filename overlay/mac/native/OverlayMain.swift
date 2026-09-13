import AppKit
import ApplicationServices
import QuartzCore

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

enum OverlayPhase {
    case collapsed
    case expanded
}

final class OverlayDelegate: NSObject, NSApplicationDelegate {
    let panel = NSPanel(
        contentRect: NSRect(x: 0, y: 0, width: 56, height: 36),
        styleMask: [.borderless, .nonactivatingPanel],
        backing: .buffered,
        defer: false
    )
    let effect = NSVisualEffectView()
    let stack = NSStackView()
    let pill = NSButton()
    let dismissHit = NSButton()
    var cardButtons: [NSButton] = []
    var extraLabels: [NSView] = []
    var cards: [[String: Any]] = []
    var intent = "trova volo Roma-Milano domani"
    var blurUnavailable = false
    var frontmost: String?
    var axTrusted = AXIsProcessTrusted()
    var chooseId: String?
    var axObserver: AXObserver?
    var phase: OverlayPhase = .collapsed
    var mark = "?"
    var timeoutMs: Double = 20_000
    var reducedMotion = false
    var dismissOutside = true
    var timeoutWork: DispatchWorkItem?
    var lastInteraction = Date()
    var profileRaw = "HIGH"
    var profileManual = false
    let profileChip = NSTextField(labelWithString: "HIGH · auto")
    var harvestFrames = 0
    var harvestLog: String?
    var harvestScene = "overlay"
    var frameDeltas: [Double] = []
    var lastFrameTs: CFTimeInterval = 0
    var displayLink: CADisplayLink?
    var wallpaper: NSWindow?

    var harvesting: Bool { harvestFrames > 0 && harvestLog != nil }

    func applicationDidFinishLaunching(_ notification: Notification) {
        parseArgs()
        guard NSScreen.main != nil else { return }
        if harvesting {
            NSApp.setActivationPolicy(.regular)
            showHarvestWallpaper()
        }
        panel.level = .floating
        panel.isOpaque = false
        panel.backgroundColor = .clear
        panel.hasShadow = false
        panel.isFloatingPanel = true
        panel.hidesOnDeactivate = false
        panel.collectionBehavior = [.canJoinAllSpaces, .fullScreenAuxiliary, .stationary]
        panel.title = "a3 overlay"
        panel.isMovableByWindowBackground = false
        // Collapsed: panel is the pill; mouse events outside the pill reach the app.
        panel.ignoresMouseEvents = false

        effect.material = .fullScreenUI
        effect.blendingMode = .behindWindow
        effect.state = .active
        effect.frame = panel.contentView?.bounds ?? .zero
        effect.autoresizingMask = [.width, .height]
        if profileRaw == "BLUR_OFF" {
            effect.state = .inactive
            panel.backgroundColor = NSColor(calibratedWhite: 0.08, alpha: 0.48)
            blurUnavailable = true
        }
        panel.contentView = effect

        dismissHit.title = ""
        dismissHit.isBordered = false
        dismissHit.wantsLayer = true
        dismissHit.layer?.backgroundColor = NSColor.clear.cgColor
        dismissHit.autoresizingMask = [.width, .height]
        dismissHit.target = self
        dismissHit.action = #selector(dismissOutsideAction)
        effect.addSubview(dismissHit)

        stack.orientation = .vertical
        stack.alignment = .centerX
        stack.spacing = 14
        stack.translatesAutoresizingMaskIntoConstraints = false
        effect.addSubview(stack)

        profileChip.stringValue = "\(profileRaw) · \(profileManual ? "manual" : "auto")"
        profileChip.font = NSFont.systemFont(ofSize: 12, weight: .regular)
        profileChip.textColor = NSColor(calibratedWhite: 0.86, alpha: 1)
        profileChip.alignment = .center
        profileChip.drawsBackground = false
        profileChip.isBezeled = false
        profileChip.identifier = NSUserInterfaceItemIdentifier("profile-pill")
        stack.addArrangedSubview(profileChip)

        pill.title = mark
        pill.bezelStyle = .inline
        pill.isBordered = false
        pill.wantsLayer = true
        pill.layer?.backgroundColor = NSColor(calibratedWhite: 0.07, alpha: 0.94).cgColor
        pill.layer?.cornerRadius = 16
        pill.layer?.borderWidth = 1.2
        pill.layer?.borderColor = NSColor(calibratedWhite: 1, alpha: 0.55).cgColor
        pill.contentTintColor = .white
        pill.font = NSFont.systemFont(ofSize: 20, weight: .bold)
        pill.target = self
        pill.action = #selector(pillTap)
        pill.identifier = NSUserInterfaceItemIdentifier("overlay-pill")
        stack.addArrangedSubview(pill)
        pill.widthAnchor.constraint(equalToConstant: 56).isActive = true
        pill.heightAnchor.constraint(equalToConstant: 36).isActive = true

        if blurUnavailable {
            let banner = label("blur unavailable", size: 13, bold: false)
            extraLabels.append(banner)
            stack.addArrangedSubview(banner)
        }

        let intentLabel = label(intent, size: 16, bold: true)
        extraLabels.append(intentLabel)
        stack.addArrangedSubview(intentLabel)
        if let frontmost {
            let f = label("frontmost \(frontmost)", size: 12, bold: false)
            extraLabels.append(f)
            stack.addArrangedSubview(f)
        }
        if axTrusted {
            let a = label("Accessibility consented", size: 12, bold: false)
            extraLabels.append(a)
            stack.addArrangedSubview(a)
            startAxObserver()
        } else {
            let a = label("Accessibility optional: contextual surfaces require consent", size: 12, bold: false)
            extraLabels.append(a)
            stack.addArrangedSubview(a)
        }

        for card in cards {
            addCardButton(card)
        }

        NSLayoutConstraint.activate([
            stack.centerXAnchor.constraint(equalTo: effect.centerXAnchor),
            stack.topAnchor.constraint(equalTo: effect.topAnchor, constant: 12),
            stack.widthAnchor.constraint(lessThanOrEqualToConstant: 720)
        ])

        let pan = NSPanGestureRecognizer(target: self, action: #selector(swipeDown(_:)))
        effect.addGestureRecognizer(pan)

        NSWorkspace.shared.notificationCenter.addObserver(
            self,
            selector: #selector(appActivated(_:)),
            name: NSWorkspace.didActivateApplicationNotification,
            object: nil
        )

        panel.makeKeyAndOrderFront(nil)
        NSEvent.addLocalMonitorForEvents(matching: .keyDown) { event in
            if event.modifierFlags.contains(.command) && event.charactersIgnoringModifiers == "w" {
                self.collapse(reason: "dismiss")
                return nil
            }
            return event
        }

        applyPhase(animated: false)

        if let chooseId {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                self.openUrl(for: chooseId)
            }
        }

        if let path = ProcessInfo.processInfo.environment["A3_OVERLAY_SHOT"] {
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.9) {
                self.saveShot(path)
            }
        }

        if harvesting {
            startDisplayLink()
        } else if let seconds = ProcessInfo.processInfo.environment["A3_OVERLAY_HOLD"] {
            DispatchQueue.main.asyncAfter(deadline: .now() + (Double(seconds) ?? 2)) {
                NSApp.terminate(nil)
            }
        }
    }

    private func addCardButton(_ card: [String: Any]) {
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
        cardButtons.append(button)
    }

    private func showHarvestWallpaper() {
        guard let screen = NSScreen.main else { return }
        let window = NSWindow(
            contentRect: screen.visibleFrame,
            styleMask: [.borderless],
            backing: .buffered,
            defer: false
        )
        window.isOpaque = true
        window.level = .normal
        window.backgroundColor = NSColor.magenta
        window.contentView = HarvestWallpaperView(frame: screen.visibleFrame)
        window.orderFront(nil)
        wallpaper = window
    }

    private func startDisplayLink() {
        if #available(macOS 14.0, *) {
            let link = effect.displayLink(target: self, selector: #selector(onDisplayLink(_:)))
            link.add(to: .main, forMode: .common)
            displayLink = link
        } else {
            fputs("CADisplayLink requires macOS 14+\n", stderr)
            exit(2)
        }
    }

    @objc func onDisplayLink(_ link: CADisplayLink) {
        if lastFrameTs == 0 {
            lastFrameTs = link.timestamp
            return
        }
        let dt = (link.timestamp - lastFrameTs) * 1000.0
        lastFrameTs = link.timestamp
        frameDeltas.append(dt)
        if frameDeltas.count >= harvestFrames {
            link.invalidate()
            displayLink = nil
            writeHarvestLog()
            NSApp.terminate(nil)
        }
    }

    private func writeHarvestLog() {
        guard let harvestLog else { return }
        let sorted = frameDeltas.sorted()
        func pct(_ p: Double) -> Double {
            guard !sorted.isEmpty else { return 0 }
            let idx = Int(Double(sorted.count - 1) * p)
            return sorted[min(max(idx, 0), sorted.count - 1)]
        }
        let iso = ISO8601DateFormatter().string(from: Date())
        let samples = frameDeltas.map { String(format: "%.4f", $0) }.joined(separator: ",")
        let body = """
        host=\(ProcessInfo.processInfo.operatingSystemVersionString) \(ProcessInfo.processInfo.processorCount) cores
        date=\(iso)
        method=CADisplayLink on-screen NSVisualEffectView
        scene=\(harvestScene)
        profile=\(profileRaw)
        blur=NSVisualEffectView.fullScreenUI.behindWindow
        harness=on-screen (not offscreen CGContext, not OverlayCompositor)
        frames=\(frameDeltas.count)
        p50_ms=\(pct(0.50))
        p95_ms=\(pct(0.95))
        p99_ms=\(pct(0.99))
        max_ms=\(sorted.last ?? 0)
        samples_ms=\(samples)

        """
        try? body.write(to: URL(fileURLWithPath: harvestLog), atomically: true, encoding: .utf8)
    }

    private func saveShot(_ path: String) {
        guard let view = panel.contentView else { return }
        let bounds = view.bounds
        guard let rep = view.bitmapImageRepForCachingDisplay(in: bounds) else { return }
        view.cacheDisplay(in: bounds, to: rep)
        try? rep.representation(using: .png, properties: [:])?.write(to: URL(fileURLWithPath: path), options: .atomic)
    }

    @objc func pillTap() {
        lastInteraction = Date()
        if phase == .collapsed {
            expand()
        }
    }

    @objc func dismissOutsideAction() {
        guard !harvesting else { return }
        guard phase == .expanded, dismissOutside else { return }
        collapse(reason: "dismiss")
    }

    @objc func appActivated(_ notification: Notification) {
        guard !harvesting else { return }
        guard phase == .expanded else { return }
        collapse(reason: "under-focus")
    }

    @objc func openCard(_ sender: NSButton) {
        openUrl(for: sender.identifier?.rawValue ?? "")
    }

    @objc func swipeDown(_ gesture: NSPanGestureRecognizer) {
        if harvesting { return }
        if gesture.state == .ended && gesture.translation(in: effect).y < -80 {
            collapse(reason: "dismiss")
        }
    }

    private func expand() {
        lastInteraction = Date()
        phase = .expanded
        applyPhase(animated: !reducedMotion)
        scheduleTimeout()
    }

    private func collapse(reason: String) {
        if harvesting { return }
        timeoutWork?.cancel()
        phase = .collapsed
        applyPhase(animated: !reducedMotion)
        _ = reason
    }

    private func applyPhase(animated: Bool) {
        let duration = (animated && !reducedMotion) ? 0.22 : 0.0
        NSAnimationContext.runAnimationGroup { ctx in
            ctx.duration = duration
            if phase == .collapsed {
                layoutCollapsed()
            } else {
                layoutExpanded()
            }
        }
        pill.title = mark
        dismissHit.isHidden = phase == .collapsed
        extraLabels.forEach { $0.isHidden = phase == .collapsed }
        cardButtons.forEach { $0.isHidden = phase == .collapsed }
        profileChip.isHidden = false
    }

    private func layoutCollapsed() {
        // Panel is pill-sized so mouse events outside the pill reach the app underneath.
        panel.ignoresMouseEvents = false
        panel.setFrame(pillFrame(), display: true)
        panel.orderFrontRegardless()
    }

    private func layoutExpanded() {
        panel.ignoresMouseEvents = false
        guard let screen = NSScreen.main else { return }
        panel.setFrame(screen.visibleFrame, display: true)
        dismissHit.frame = effect.bounds
    }

    private func pillFrame() -> NSRect {
        guard let screen = NSScreen.main else {
            return NSRect(x: 0, y: 0, width: 56, height: 36)
        }
        let w: CGFloat = 168
        let h: CGFloat = 72
        return NSRect(
            x: screen.visibleFrame.maxX - w - 28,
            y: screen.visibleFrame.maxY - h - 28,
            width: w,
            height: h
        )
    }

    private func scheduleTimeout() {
        if harvesting { return }
        timeoutWork?.cancel()
        guard phase == .expanded else { return }
        let work = DispatchWorkItem { [weak self] in
            self?.collapse(reason: "timeout")
        }
        timeoutWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + timeoutMs / 1000.0, execute: work)
    }

    private func openUrl(for id: String) {
        mark = "…"
        collapse(reason: "dispatch")
        let url = cards.first { ($0["id"] as? String) == id }.flatMap { $0["url"] as? String }
            ?? cards.first.flatMap { $0["url"] as? String }
        if let url, let parsed = URL(string: url) {
            NSWorkspace.shared.open(parsed)
            mark = "✓"
        } else {
            mark = "?"
        }
        pill.title = mark
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
                blurUnavailable = obj["blur_unavailable"] as? Bool ?? false
                frontmost = obj["frontmost"] as? String
                cards = obj["cards"] as? [[String: Any]] ?? []
                dismissOutside = obj["dismiss_outside"] as? Bool ?? true
                reducedMotion = obj["reduced_motion"] as? Bool ?? false
                if let t = obj["timeout_ms"] as? Double {
                    timeoutMs = t
                } else if let t = obj["timeout_ms"] as? Int {
                    timeoutMs = Double(t)
                }
                if let p = obj["phase"] as? String, p.uppercased() == "EXPANDED" {
                    phase = .expanded
                }
                if let m = obj["mark"] as? String {
                    mark = markGlyph(m)
                }
            }
        }
        if args.contains("--expanded") {
            phase = .expanded
        }
        if args.contains("--reduced") {
            reducedMotion = true
        }
        if let idx = args.firstIndex(of: "--profile"), idx + 1 < args.count {
            let raw = args[idx + 1].uppercased().replacingOccurrences(of: "-", with: "_")
            if raw == "AUTO" || raw == "DEFAULT" {
                profileManual = false
            } else {
                profileRaw = raw
                profileManual = true
            }
        }
        if let idx = args.firstIndex(of: "--scene"), idx + 1 < args.count {
            harvestScene = args[idx + 1].lowercased()
        }
        if let idx = args.firstIndex(of: "--frames"), idx + 1 < args.count {
            harvestFrames = Int(args[idx + 1]) ?? 0
        }
        if let idx = args.firstIndex(of: "--log"), idx + 1 < args.count {
            harvestLog = args[idx + 1]
        }
        if profileRaw == "MID" {
            reducedMotion = true
        }
        if profileRaw == "BLUR_OFF" {
            blurUnavailable = true
        }
        if ProcessInfo.processInfo.environment["A3_REDUCED_MOTION"] == "1" {
            reducedMotion = true
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
        if harvestScene == "catalog" {
            var i = cards.count
            while cards.count < 12 {
                i += 1
                cards.append([
                    "id": "catalog.\(i)",
                    "title": "Catalog card \(i) Roma–Milano",
                    "price": "\(20 + i).00 EUR",
                    "url": "https://www.google.com/search?q=roma+milano+\(i)"
                ])
            }
            phase = .expanded
        }
        if harvesting {
            phase = .expanded
            timeoutMs = 3_600_000
        }
        if let app = NSWorkspace.shared.frontmostApplication {
            frontmost = frontmost ?? app.localizedName
        }
    }

    private func markGlyph(_ raw: String) -> String {
        switch raw.uppercased() {
        case "PENDING": return "…"
        case "DONE": return "✓"
        default: return "?"
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

final class HarvestWallpaperView: NSView {
    override func draw(_ dirtyRect: NSRect) {
        super.draw(dirtyRect)
        let cell: CGFloat = 48
        var y: CGFloat = 0
        var row = 0
        while y < bounds.height {
            var x: CGFloat = 0
            var col = 0
            while x < bounds.width {
                let hue = CGFloat((row + col) % 8) / 8.0
                NSColor(calibratedHue: hue, saturation: 0.55, brightness: 0.72, alpha: 1).setFill()
                NSBezierPath.fill(NSRect(x: x, y: y, width: cell, height: cell))
                x += cell
                col += 1
            }
            y += cell
            row += 1
        }
    }
}
