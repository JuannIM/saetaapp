import SwiftUI
import WebKit

struct SAETAWebView: UIViewRepresentable {
    let url: URL
    let onBalanceFound: (Double) -> Void
    
    func makeUIView(context: Context) -> WKWebView {
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        
        let config = WKWebViewConfiguration()
        config.defaultWebpagePreferences = prefs
        
        // Inyectamos script para buscar el saldo continuamente
        let js = """
        setInterval(function() {
            var text = document.body.innerText;
            // Busca patrón de saldo, ej: Saldo: $ 1234.56
            var match = text.match(/[$]\\s*([0-9]+[.,][0-9]{2})/);
            if (match && match.length > 1) {
                window.webkit.messageHandlers.balanceHandler.postMessage(match[1]);
            }
        }, 1000);
        """
        
        let script = WKUserScript(source: js, injectionTime: .atDocumentEnd, forMainFrameOnly: false)
        config.userContentController.addUserScript(script)
        config.userContentController.add(context.coordinator, name: "balanceHandler")
        
        let webView = WKWebView(frame: .zero, configuration: config)
        webView.navigationDelegate = context.coordinator
        
        let request = URLRequest(url: url)
        webView.load(request)
        
        return webView
    }
    
    func updateUIView(_ uiView: WKWebView, context: Context) {}
    
    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }
    
    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        var parent: SAETAWebView
        var hasReported = false
        
        init(_ parent: SAETAWebView) {
            self.parent = parent
        }
        
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard !hasReported, let balanceStr = message.body as? String else { return }
            
            let normalized = balanceStr.replacingOccurrences(of: ",", with: ".")
            if let balance = Double(normalized) {
                hasReported = true
                DispatchQueue.main.async {
                    self.parent.onBalanceFound(balance)
                }
            }
        }
    }
}
