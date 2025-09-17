import Foundation
import WebKit
import UIKit

class WebViewBridge: NSObject, WKScriptMessageHandler {
    
    private weak var hostWebView: WKWebView?
    private weak var flutterWebView: WKWebView?
    private weak var viewController: ViewController?
    
    private var runScriptOnNextFinish = false
    private var collapseIframeBySideEffect = false
    private var currentLang: String = "EN"
    var isFlutterExpanded = false
    
    var shouldRunScriptOnNextFinish: Bool {
        return runScriptOnNextFinish
    }
    
    var shouldCollapseIframeBySideEffect: Bool {
        return collapseIframeBySideEffect
    }
    
    var currentHostWebLang: String {
        return currentLang
    }
    
    init(hostWebView: WKWebView, flutterWebView: WKWebView, viewController: ViewController) {
        self.hostWebView = hostWebView
        self.flutterWebView = flutterWebView
        self.viewController = viewController
        super.init()
    }
    
    func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
        var messageBody: [String: Any]
        var action: String
        
        if let bodyString = message.body as? String {
            guard let data = bodyString.data(using: .utf8),
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let actionValue = json["action"] as? String else {
                print("Failed to parse message: \(message.body)")
                return
            }
            messageBody = json
            action = actionValue
        } else if let bodyDict = message.body as? [String: Any],
                  let actionValue = bodyDict["action"] as? String {
            messageBody = bodyDict
            action = actionValue
        } else {
            print("Missing 'action' in message: \(message.body)")
            return
        }
        
        let shouldNotify = messageBody["runScriptOnHostPageFinish"] as? Bool ?? false
        runScriptOnNextFinish = shouldNotify
        
        let shouldCollapseIFrame = messageBody["collapseIframeAfterThisAction"] as? Bool ?? false
        collapseIframeBySideEffect = shouldCollapseIFrame
        
        DispatchQueue.main.async {
            print("Received action: \(action)")
            self.handleAction(action: action, messageBody: messageBody)
        }
    }
    
    private func handleAction(action: String, messageBody: [String: Any]) {
        switch action {
        case "flutterIsReady":
            let uuid = FirstLaunchHandler.getOrCreateUUID()
            print("flutterIsReady - App UUID: \(uuid)")
            
            let js = """
                window.postMessage({ 
                    action: "sendInitialConfig", 
                    lang: "\(currentLang)", 
                    uuid: "\(uuid)" 
                }, "*");
            """
            
            flutterWebView?.evaluateJavaScript(js, completionHandler: nil)
            
        case "updateLang":
            if let hasSiteLang = messageBody["hasSiteLang"] as? Bool,
               let lang = messageBody["lang"] as? String {
                updateLang(hasSiteLang: hasSiteLang, lang: lang)
            }
            
        case "expandIframe":
            viewController?.expandFlutterWebView()
            
        case "collapseIframe":
            viewController?.collapseFlutterWebView()
            
        case "navigateHost":
            if let data = messageBody["data"] as? [String: Any],
               let urlString = data["url"] as? String,
               let url = URL(string: urlString) {
                hostWebView?.load(URLRequest(url: url))
            }
            
        case "host:navigateHost":
            break
            
        case "openWindow":
            print("onFlutterMessage called with: \(messageBody)")
            if let data = messageBody["data"] as? [String: Any],
               let urlString = data["url"] as? String,
               let url = URL(string: urlString) {
                print("Opening external browser to: \(urlString)")
                UIApplication.shared.open(url, options: [:], completionHandler: nil)
            }
            
        default:
            let parts = action.split(separator: ":")
            if parts.count == 2 {
                let partsArray = parts.map { String($0) }
                sendRequestWithCoroutine(message: messageBody, parts: partsArray)
            }
        }
    }
    
    func readLanguageFromLocalStorage() {
        let js = """
            (function() {
                var hasSiteLang = "siteLang" in localStorage;
                var lang = localStorage.getItem('siteLang') || '';
                console.log("siteLang exists:", hasSiteLang, "| value:", lang);
                window.webkit.messageHandlers.AndroidBridge.postMessage({
                    action: "updateLang",
                    hasSiteLang: hasSiteLang,
                    lang: lang
                });
            })();
        """
        
        hostWebView?.evaluateJavaScript(js, completionHandler: nil)
    }
    
    func updateLang(hasSiteLang: Bool, lang: String) {
        print("is siteLang exist in localStorage: \(hasSiteLang)")
        print("updateLang called with: \(lang)")
        currentLang = lang
        
        DispatchQueue.main.async {
            let js = """
                window.postMessage({ 
                    action: "sendLang", 
                    lang: "\(self.currentLang)" 
                }, "*");
            """
            
            self.flutterWebView?.evaluateJavaScript(js, completionHandler: nil)
        }
    }
    
    func sendRequestWithCoroutine(message: [String: Any], parts: [String]) {
        guard parts.count >= 2 else { return }
        
        Task {
            do {
                var requestBodyMap: [String: Any] = ["action": parts[1]]
                
                if let data = message["data"] as? [String: Any],
                   let rawData = data["data"] {
                    requestBodyMap["data"] = rawData
                    print("rawData: \(rawData), dataType: \(type(of: rawData))")
                } else {
                    print("data['data'] is null")
                }
                
                let jsonData = try JSONSerialization.data(withJSONObject: requestBodyMap)
                
                var request = URLRequest(url: URL(string: "https://tigerapi.platypusoft.com/api/v001/javascript/getScript")!)
                request.httpMethod = "POST"
                request.setValue("application/json; charset=utf-8", forHTTPHeaderField: "Content-Type")
                request.httpBody = jsonData
                
                let (data, response) = try await URLSession.shared.data(for: request)
                
                if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode == 200 {
                    print("success")
                    let apiResponse = try JSONDecoder().decode(ApiResponse.self, from: data)
                    
                    await MainActor.run {
                        print("result: \(apiResponse.result)")
                        print("message: \(apiResponse.resultMessage)")
                        print("javascriptCode: \(apiResponse.javascriptCode)")
                        
                        if apiResponse.result == 1 {
                            if parts[0] == "host" {
                                self.hostWebView?.evaluateJavaScript(apiResponse.javascriptCode, completionHandler: nil)
                            } else {
                                self.flutterWebView?.evaluateJavaScript(apiResponse.javascriptCode, completionHandler: nil)
                            }
                        }
                    }
                } else {
                    print("Error: \((response as? HTTPURLResponse)?.statusCode ?? 0)")
                }
                
            } catch {
                await MainActor.run {
                    print("Exception: \(error)")
                }
            }
        }
    }
}
