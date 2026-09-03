// SAETABusMapView.swift
// SAETA Saldo App - Vista Web para seguimiento de colectivos en tiempo real

import SwiftUI
import WebKit

// MARK: - Modo de visualización
enum BusViewMode: String, CaseIterable, Identifiable {
    case mapa = "mapa-de-buses"
    case cuandoViene = "cuando-viene"

    var id: String { rawValue }

    var title: String {
        switch self {
        case .mapa: return "Mapa en Vivo"
        case .cuandoViene: return "¿Cuándo Llega?"
        }
    }

    var icon: String {
        switch self {
        case .mapa: return "map.fill"
        case .cuandoViene: return "clock.fill"
        }
    }

    var urlString: String {
        "https://salta.miredbus.com.ar/#/\(rawValue)"
    }
}

// MARK: - Representable de WKWebView
struct SAETABusMapView: UIViewRepresentable {
    let mode: BusViewMode
    @Binding var isLoading: Bool
    let reloadTrigger: Int

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true

        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences = prefs

        // Inyectamos CSS para ocultar cabeceras/footers y maximizar el mapa
        let css = """
            /* Ocultar barra superior, navegación y pie del portal */
            .header,
            .md-app-toolbar,
            .md-toolbar,
            header,
            nav,
            .navbar,
            .md-tabs-navigation,
            .md-steppers-navigation,
            .footer,
            footer,
            .back,
            .back-btn,
            .back-button {
                display: none !important;
            }

            /* Quitar márgenes excesivos para aprovechar la pantalla */
            body, .md-app, html {
                background: #F2F2F7 !important;
            }
            .md-app-content,
            .md-app-scroller,
            #redbus > div,
            main.mapa-de-buses,
            main.cuando-viene {
                margin: 0 !important;
                padding: 10px 14px !important;
            }

            /* Adaptar el contenedor del mapa para mobile */
            .map-view, #map-mapa-de-buses, #map-view {
                height: 68vh !important;
                min-height: 440px !important;
                max-height: none !important;
                border-radius: 16px !important;
                box-shadow: 0 4px 16px rgba(0,0,0,0.1) !important;
                border: 1px solid rgba(0,0,0,0.06) !important;
            }

            /* Selector de líneas estilizado */
            .select-box-container {
                margin-bottom: 10px !important;
            }

            /* Ocultar títulos redundantes */
            .map-view-top-elements h2 {
                font-size: 16px !important;
                margin: 0 0 6px 0 !important;
                color: #1C1C1E !important;
                font-family: -apple-system, BlinkMacSystemFont, sans-serif !important;
            }
        """

        let js = """
        (function() {
            function injectStyles() {
                if (document.getElementById('saeta-bus-map-css')) return;
                var style = document.createElement('style');
                style.id = 'saeta-bus-map-css';
                style.textContent = `\(css.replacingOccurrences(of: "`", with: "\\`"))`;
                (document.head || document.documentElement).appendChild(style);
            }
            injectStyles();
            setTimeout(injectStyles, 500);
            setTimeout(injectStyles, 1500);
            setTimeout(injectStyles, 3000);

            if (window.MutationObserver) {
                var observer = new MutationObserver(function() {
                    injectStyles();
                });
                observer.observe(document.documentElement, { childList: true, subtree: true });
            }
        })();
        """

        let userScript = WKUserScript(
            source: js,
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: false
        )
        config.userContentController.addUserScript(userScript)

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        webView.backgroundColor = .systemGroupedBackground
        webView.scrollView.backgroundColor = .systemGroupedBackground
        webView.isOpaque = false

        context.coordinator.webView = webView
        context.coordinator.currentMode = mode

        loadURL(mode.urlString, in: webView)

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        // Si cambió el modo (de Mapa a ¿Cuándo Llega?), recargamos la nueva URL
        if context.coordinator.currentMode != mode {
            context.coordinator.currentMode = mode
            DispatchQueue.main.async {
                self.isLoading = true
            }
            loadURL(mode.urlString, in: uiView)
        }

        // Si se disparó el botón de refrescar manual
        if context.coordinator.lastReloadTrigger != reloadTrigger {
            context.coordinator.lastReloadTrigger = reloadTrigger
            DispatchQueue.main.async {
                self.isLoading = true
            }
            uiView.reload()
        }
    }

    private func loadURL(_ urlStr: String, in webView: WKWebView) {
        guard let url = URL(string: urlStr) else { return }
        var request = URLRequest(url: url)
        request.setValue(
            "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
            forHTTPHeaderField: "User-Agent"
        )
        request.setValue("https://salta.miredbus.com.ar/", forHTTPHeaderField: "Referer")
        webView.load(request)
    }

    // MARK: - Coordinator
    class Coordinator: NSObject, WKNavigationDelegate {
        var parent: SAETABusMapView
        weak var webView: WKWebView?
        var currentMode: BusViewMode?
        var lastReloadTrigger: Int = 0

        init(_ parent: SAETABusMapView) {
            self.parent = parent
        }

        // Aceptar el certificado SSL de la CA intermedia del portal SAETA
        func webView(
            _ webView: WKWebView,
            didReceive challenge: URLAuthenticationChallenge,
            completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
                  let serverTrust = challenge.protectionSpace.serverTrust else {
                completionHandler(.performDefaultHandling, nil)
                return
            }
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            // Dar tiempo breve para que Vue monte los elementos del mapa
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.2) {
                withAnimation(.easeOut(duration: 0.25)) {
                    self.parent.isLoading = false
                }
            }
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
            }
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            DispatchQueue.main.async {
                self.parent.isLoading = false
            }
        }
    }
}
