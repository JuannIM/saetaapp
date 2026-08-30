// SAETAWebView.swift
// SAETA Saldo App - WebView integrada para consulta de saldo con captcha
//
// Usa WKWebView con NSURLAuthenticationChallengeSender para aceptar
// el certificado SSL de la CA autofirmada del portal de SAETA/RedBus.
// Inyecta JavaScript para:
//   1. Autocompletar el número de tarjeta en el formulario del portal
//   2. Detectar el saldo en el HTML resultante y devolverlo a Swift

import SwiftUI
import WebKit

// MARK: - Vista Web SAETA
struct SAETAWebView: UIViewRepresentable {
    let cardNumber: String
    let onBalanceFound: (Double) -> Void

    func makeUIView(context: Context) -> WKWebView {
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true

        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences = prefs

        // Script que se ejecuta en cada página:
        // 1. Intenta completar el campo de número de tarjeta automáticamente
        // 2. Monitorea el DOM buscando el saldo con distintos patrones
        let cardNum = cardNumber
        let js = """
        (function() {
            var cardNumber = '\(cardNum)';
            var reported = false;

            function tryFillForm() {
                // Selectores comunes para el campo de número de tarjeta
                var selectors = [
                    'input[name*="tarjeta"]',
                    'input[name*="card"]',
                    'input[name*="numero"]',
                    'input[placeholder*="arjeta"]',
                    'input[placeholder*="úmero"]',
                    'input[type="text"]:not([name*="captcha"])',
                    'input[type="number"]'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var el = document.querySelector(selectors[i]);
                    if (el && !el.value) {
                        el.value = cardNumber;
                        el.dispatchEvent(new Event('input', { bubbles: true }));
                        el.dispatchEvent(new Event('change', { bubbles: true }));
                        break;
                    }
                }
            }

            function extractBalance(text) {
                // Patrones para detectar el saldo en distintos formatos del portal
                var patterns = [
                    /saldo[^\\d]*([\\d]+[.,][\\d]{2})/i,
                    /\\$\\s*([\\d]+[.,][\\d]{2})/,
                    /balance[^\\d]*([\\d]+[.,][\\d]{2})/i,
                    /([\\d]{1,6}[.,][\\d]{2})\\s*(?:pesos|ARS|\\$)/i
                ];
                for (var p = 0; p < patterns.length; p++) {
                    var m = text.match(patterns[p]);
                    if (m && m[1]) {
                        return m[1].replace(',', '.');
                    }
                }
                return null;
            }

            function checkForBalance() {
                if (reported) return;
                var bodyText = document.body ? document.body.innerText : '';
                var bal = extractBalance(bodyText);
                if (bal) {
                    var num = parseFloat(bal);
                    if (!isNaN(num) && num >= 0) {
                        reported = true;
                        window.webkit.messageHandlers.balanceHandler.postMessage(String(num));
                    }
                }
            }

            // Intentar rellenar el formulario y monitorear el saldo
            setTimeout(tryFillForm, 500);
            setTimeout(tryFillForm, 1500);
            setTimeout(tryFillForm, 3000);

            // Monitorear cambios en el DOM buscando el saldo
            setInterval(checkForBalance, 1000);

            // MutationObserver para detectar cambios dinámicos
            if (window.MutationObserver) {
                var observer = new MutationObserver(function() {
                    checkForBalance();
                });
                observer.observe(document.body || document.documentElement, {
                    childList: true, subtree: true, characterData: true
                });
            }
        })();
        """

        let script = WKUserScript(
            source: js,
            injectionTime: .atDocumentEnd,
            forMainFrameOnly: false
        )
        config.userContentController.addUserScript(script)
        config.userContentController.add(context.coordinator, name: "balanceHandler")

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator

        // Cargar el portal de SAETA
        if let url = URL(string: BalanceService.portalURL) {
            var request = URLRequest(url: url)
            request.setValue(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
                forHTTPHeaderField: "User-Agent"
            )
            webView.load(request)
        }

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    // MARK: - Coordinator
    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: SAETAWebView
        var hasReported = false

        init(_ parent: SAETAWebView) {
            self.parent = parent
        }

        // MARK: Aceptar certificado SSL autofirmado del portal SAETA
        // El portal usa Let's Encrypt pero con una CA intermedia no estándar
        // que iOS no reconoce. Este delegate lo acepta explícitamente.
        func webView(
            _ webView: WKWebView,
            didReceive challenge: URLAuthenticationChallenge,
            completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void
        ) {
            if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
               let serverTrust = challenge.protectionSpace.serverTrust {
                // Aceptamos el certificado del portal SAETA aunque tenga CA autofirmada
                let credential = URLCredential(trust: serverTrust)
                completionHandler(.useCredential, credential)
            } else {
                completionHandler(.performDefaultHandling, nil)
            }
        }

        // MARK: Recibir saldo desde JavaScript
        func userContentController(
            _ userContentController: WKUserContentController,
            didReceive message: WKScriptMessage
        ) {
            guard !hasReported, let balanceStr = message.body as? String else { return }
            let normalized = balanceStr.replacingOccurrences(of: ",", with: ".")
            if let balance = Double(normalized), balance >= 0 {
                hasReported = true
                DispatchQueue.main.async {
                    self.parent.onBalanceFound(balance)
                }
            }
        }
    }
}
