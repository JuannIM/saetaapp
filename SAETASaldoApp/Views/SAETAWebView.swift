// SAETAWebView.swift
// SAETA Saldo App - WebView integrada para consulta de saldo con aspecto nativo
//
// Estrategia:
//  1. Navega directo a #/card-credit (formulario de consulta de saldo)
//  2. Acepta el certificado SSL de CA autofirmada del portal SAETA/RedBus
//  3. Inyecta CSS para ocultar header, navbar y footer → aspecto nativo
//  4. Inyecta JS para autocompletar el número de tarjeta en el formulario
//  5. Detecta el saldo en el DOM y lo devuelve a Swift automáticamente

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

        // ── CSS de aspecto nativo ──────────────────────────────────────────
        // Oculta header, navbar, footer y elementos de navegación del portal
        // para que se vea como una pantalla nativa de la app.
        let css = """
            /* Ocultar header y toolbar */
            .header,
            .md-app-toolbar,
            .md-toolbar,
            header,
            nav,
            .navbar,
            .md-tabs-navigation,
            .md-steppers-navigation { display: none !important; }

            /* Ocultar footer */
            .footer,
            footer { display: none !important; }

            /* Quitar márgen superior que dejó el header */
            .md-app-content,
            .md-app-scroller,
            #redbus > div { margin-top: 0 !important; padding-top: 8px !important; }

            /* Fondo blanco limpio */
            body, .md-app { background: #FFFFFF !important; }

            /* Mejorar tipografía en mobile */
            .md-card { border-radius: 12px !important; box-shadow: 0 2px 12px rgba(0,0,0,0.08) !important; margin: 12px !important; }
            .md-button.md-raised.md-primary { border-radius: 10px !important; }
        """

        // ── JavaScript de auto-fill y detección de saldo ───────────────────
        let cardNum = cardNumber.filter { $0.isNumber } // solo dígitos
        let js = """
        (function() {
            var cardNumber = '\(cardNum)';
            var reported = false;

            // Inyectar CSS de aspecto nativo
            function injectStyles() {
                if (document.getElementById('saeta-native-css')) return;
                var style = document.createElement('style');
                style.id = 'saeta-native-css';
                style.textContent = `\(css.replacingOccurrences(of: "`", with: "\\`"))`;
                (document.head || document.documentElement).appendChild(style);
            }

            // Rellenar todos los inputs de número de tarjeta que aparezcan
            function tryFillForm() {
                var filled = false;
                // Selectores comunes en Vue Material inputs del portal RedBus
                var selectors = [
                    'input[type="number"]',
                    'input[type="text"]',
                    'input[name*="tarjeta"]',
                    'input[name*="card"]',
                    'input[name*="numero"]',
                    'input[placeholder*="arjeta"]',
                    'input[placeholder*="mero"]',
                    'input[placeholder*="Card"]',
                    '.md-input'
                ];
                for (var i = 0; i < selectors.length; i++) {
                    var inputs = document.querySelectorAll(selectors[i]);
                    inputs.forEach(function(el) {
                        if (!el.value || el.value === '') {
                            el.value = cardNumber;
                            el.dispatchEvent(new Event('input',  { bubbles: true }));
                            el.dispatchEvent(new Event('change', { bubbles: true }));
                            el.dispatchEvent(new Event('blur',   { bubbles: true }));
                            filled = true;
                        }
                    });
                }
                return filled;
            }

            // Extraer saldo del texto de la página
            function extractBalance(text) {
                var patterns = [
                    /saldo[^\\d$]{0,20}\\$?\\s*([\\d]{1,6}[.,][\\d]{2})/i,
                    /\\$\\s*([\\d]{1,6}[.,][\\d]{2})/,
                    /balance[^\\d$]{0,20}\\$?\\s*([\\d]{1,6}[.,][\\d]{2})/i,
                    /([\\d]{1,6}[.,][\\d]{2})\\s*(?:pesos|ARS)/i,
                    /credito[^\\d$]{0,30}\\$?\\s*([\\d]{1,6}[.,][\\d]{2})/i
                ];
                for (var p = 0; p < patterns.length; p++) {
                    var m = text.match(patterns[p]);
                    if (m && m[1]) {
                        var val = m[1].replace(',', '.');
                        var n = parseFloat(val);
                        if (!isNaN(n) && n >= 0 && n < 100000) return String(n);
                    }
                }
                return null;
            }

            function checkForBalance() {
                if (reported) return;
                var bodyText = document.body ? document.body.innerText : '';
                var bal = extractBalance(bodyText);
                if (bal) {
                    reported = true;
                    window.webkit.messageHandlers.balanceHandler.postMessage(bal);
                }
            }

            // Inicializar al cargar
            function init() {
                injectStyles();
                tryFillForm();
            }

            // Ejecutar en distintos momentos para cubrir el renderizado de Vue
            init();
            setTimeout(init, 500);
            setTimeout(init, 1500);
            setTimeout(init, 3000);

            // Monitorear cambios del DOM (Vue actualiza la UI dinámicamente)
            if (window.MutationObserver) {
                var observer = new MutationObserver(function(mutations) {
                    injectStyles();
                    tryFillForm();
                    checkForBalance();
                });
                observer.observe(document.documentElement, {
                    childList: true, subtree: true, characterData: true
                });
            }

            // Revisar saldo periódicamente
            setInterval(checkForBalance, 1500);
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

        // Fondo blanco para evitar flash negro al cargar
        webView.backgroundColor = .white
        webView.scrollView.backgroundColor = .white
        webView.isOpaque = false

        // Cargar directamente la sección de consulta de saldo (#/card-credit)
        if let url = URL(string: "https://salta.miredbus.com.ar/#/card-credit") {
            var request = URLRequest(url: url)
            request.setValue(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
                forHTTPHeaderField: "User-Agent"
            )
            request.setValue("https://salta.miredbus.com.ar/", forHTTPHeaderField: "Referer")
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

        // MARK: Aceptar certificado SSL del portal SAETA
        // El portal usa un certificado con CA autofirmada en la cadena
        // que iOS rechaza por defecto en URLSession.
        // WKWebView con este delegate puede aceptarlo explícitamente.
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
            // Aceptar el certificado del portal SAETA
            completionHandler(.useCredential, URLCredential(trust: serverTrust))
        }

        // MARK: Inyectar CSS al finalizar navegación
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            // Re-inyectar los estilos después de cada navegación del SPA
            let cssInject = """
                (function() {
                    if (!document.getElementById('saeta-native-css')) {
                        var s = document.createElement('style');
                        s.id = 'saeta-native-css';
                        s.textContent = '.header,.md-app-toolbar,.md-toolbar,header,nav,.navbar,.md-tabs-navigation,.footer,footer{display:none!important}.md-app-content,.md-app-scroller{margin-top:0!important;padding-top:8px!important}body,.md-app{background:#fff!important}.md-card{border-radius:12px!important;box-shadow:0 2px 12px rgba(0,0,0,.08)!important;margin:12px!important}.md-button.md-raised.md-primary{border-radius:10px!important}';
                        (document.head||document.documentElement).appendChild(s);
                    }
                })();
            """
            webView.evaluateJavaScript(cssInject, completionHandler: nil)
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
