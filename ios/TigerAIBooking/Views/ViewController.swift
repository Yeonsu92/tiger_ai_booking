import UIKit
import WebKit

class ViewController: UIViewController {
    
    private var hostWebView: WKWebView!
    private var flutterWebView: WKWebView!
    private var splashImageView: UIImageView!
    private var webViewBridge: WebViewBridge!
    
    private let siteLang: String = "EN"
    private var isBackHandlerEnabled = true
    private let useTestServer = false
    
    private var hostWebViewWidthConstraint: NSLayoutConstraint!
    private var hostWebViewHeightConstraint: NSLayoutConstraint!
    private var flutterWebViewWidthConstraint: NSLayoutConstraint!
    private var flutterWebViewHeightConstraint: NSLayoutConstraint!
    private var flutterWebViewBottomConstraint: NSLayoutConstraint!
    private var flutterWebViewTrailingConstraint: NSLayoutConstraint!
    private var flutterWebViewLeadingConstraint: NSLayoutConstraint?
    private var flutterWebViewTopConstraint: NSLayoutConstraint?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        setupUI()
        setupWebViews()
        setupConstraints()
        setupBridge()
        loadWebViews()
        
        KeyboardUtils.setupKeyboardHandling(for: self)
    }
    
    private func setupUI() {
        view.backgroundColor = .white
        
        let hostConfig = WKWebViewConfiguration()
        hostConfig.allowsInlineMediaPlayback = true
        hostConfig.mediaTypesRequiringUserActionForPlayback = []
        
        let flutterConfig = WKWebViewConfiguration()
        flutterConfig.allowsInlineMediaPlayback = true
        flutterConfig.mediaTypesRequiringUserActionForPlayback = []
        
        hostWebView = WKWebView(frame: .zero, configuration: hostConfig)
        flutterWebView = WKWebView(frame: .zero, configuration: flutterConfig)
        
        hostWebView.translatesAutoresizingMaskIntoConstraints = false
        flutterWebView.translatesAutoresizingMaskIntoConstraints = false
        
        hostWebView.isHidden = true
        flutterWebView.isHidden = true
        flutterWebView.backgroundColor = .clear
        flutterWebView.isOpaque = false
        
        splashImageView = UIImageView()
        splashImageView.translatesAutoresizingMaskIntoConstraints = false
        splashImageView.contentMode = .scaleAspectFit
        splashImageView.image = UIImage(named: "logo")
        
        view.addSubview(hostWebView)
        view.addSubview(flutterWebView)
        view.addSubview(splashImageView)
    }
    
    private func setupWebViews() {
        hostWebView.navigationDelegate = self
        flutterWebView.navigationDelegate = self
        
        let hostSettings = hostWebView.configuration.preferences
        hostSettings.javaScriptEnabled = true
        
        let flutterSettings = flutterWebView.configuration.preferences
        flutterSettings.javaScriptEnabled = true
    }
    
    private func setupConstraints() {
        hostWebViewWidthConstraint = hostWebView.widthAnchor.constraint(equalTo: view.widthAnchor)
        hostWebViewHeightConstraint = hostWebView.heightAnchor.constraint(equalTo: view.heightAnchor)
        
        flutterWebViewWidthConstraint = flutterWebView.widthAnchor.constraint(equalToConstant: 60)
        flutterWebViewHeightConstraint = flutterWebView.heightAnchor.constraint(equalToConstant: 60)
        flutterWebViewBottomConstraint = flutterWebView.bottomAnchor.constraint(equalTo: view.safeAreaLayoutGuide.bottomAnchor, constant: -120)
        flutterWebViewTrailingConstraint = flutterWebView.trailingAnchor.constraint(equalTo: view.trailingAnchor, constant: -16)
        
        NSLayoutConstraint.activate([
            hostWebView.topAnchor.constraint(equalTo: view.topAnchor),
            hostWebView.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            hostWebViewWidthConstraint,
            hostWebViewHeightConstraint,
            
            flutterWebViewWidthConstraint,
            flutterWebViewHeightConstraint,
            flutterWebViewBottomConstraint,
            flutterWebViewTrailingConstraint,
            
            splashImageView.centerXAnchor.constraint(equalTo: view.centerXAnchor),
            splashImageView.centerYAnchor.constraint(equalTo: view.centerYAnchor),
            splashImageView.widthAnchor.constraint(equalToConstant: 100),
            splashImageView.heightAnchor.constraint(equalToConstant: 100)
        ])
    }
    
    private func setupBridge() {
        webViewBridge = WebViewBridge(hostWebView: hostWebView, flutterWebView: flutterWebView, viewController: self)
        
        let hostContentController = hostWebView.configuration.userContentController
        hostContentController.add(webViewBridge, name: "AndroidBridge")
        
        let flutterContentController = flutterWebView.configuration.userContentController
        flutterContentController.add(webViewBridge, name: "AndroidBridge")
    }
    
    private func loadWebViews() {
        let hostURL: String
        let flutterURL: String
        
        if useTestServer {
            hostURL = "https://dev-renew.tigerbooking.golf/"
            flutterURL = "https://tigertest.platypusoft.com/flutter"
        } else {
            hostURL = "https://www.tigerbooking.golf"
            flutterURL = "https://tiger.platypusoft.com/flutter"
        }
        
        if let url = URL(string: hostURL) {
            hostWebView.load(URLRequest(url: url))
        }
        
        if let url = URL(string: flutterURL) {
            flutterWebView.load(URLRequest(url: url))
        }
    }
    
    func collapseFlutterWebView() {
        print("collapseFlutterWebView start")
        
        flutterWebViewLeadingConstraint?.isActive = false
        flutterWebViewTopConstraint?.isActive = false
        
        flutterWebViewWidthConstraint.constant = 60
        flutterWebViewHeightConstraint.constant = 60
        flutterWebViewBottomConstraint.constant = -120
        flutterWebViewTrailingConstraint.isActive = true
        
        UIView.animate(withDuration: 0.3) {
            self.view.layoutIfNeeded()
        } completion: { _ in
            let js = "window.postMessage({ action: \"iframeCollapsed\" }, \"*\");"
            self.flutterWebView.evaluateJavaScript(js, completionHandler: nil)
        }
        
        hostWebView.isUserInteractionEnabled = true
        webViewBridge.isFlutterExpanded = false
    }
    
    func expandFlutterWebView() {
        hostWebView.isUserInteractionEnabled = false
        webViewBridge.isFlutterExpanded = true
        
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.2) {
            self.flutterWebViewTrailingConstraint.isActive = false
            
            if self.flutterWebViewLeadingConstraint == nil {
                self.flutterWebViewLeadingConstraint = self.flutterWebView.leadingAnchor.constraint(equalTo: self.view.leadingAnchor)
            }
            if self.flutterWebViewTopConstraint == nil {
                self.flutterWebViewTopConstraint = self.flutterWebView.topAnchor.constraint(equalTo: self.view.topAnchor)
            }
            
            self.flutterWebViewLeadingConstraint?.isActive = true
            self.flutterWebViewTopConstraint?.isActive = true
            
            self.flutterWebViewWidthConstraint.constant = self.view.bounds.width
            self.flutterWebViewHeightConstraint.constant = self.view.bounds.height
            self.flutterWebViewBottomConstraint.constant = 0
            
            UIView.animate(withDuration: 0.3) {
                self.view.layoutIfNeeded()
            } completion: { _ in
                let js = "window.postMessage({ action: \"iframeExpanded\" }, \"*\");"
                self.flutterWebView.evaluateJavaScript(js, completionHandler: nil)
            }
        }
    }
    
    func hideSplashScreen() {
        DispatchQueue.main.async {
            if !self.splashImageView.isHidden {
                self.splashImageView.isHidden = true
            }
            
            if self.hostWebView.isHidden {
                self.hostWebView.isHidden = false
            }
            
            if self.flutterWebView.isHidden {
                self.flutterWebView.isHidden = false
            }
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        
        hostWebView.load(URLRequest(url: URL(string: "about:blank")!))
        flutterWebView.load(URLRequest(url: URL(string: "about:blank")!))
    }
}

extension ViewController: WKNavigationDelegate {
    func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
        if webView == hostWebView {
            if webViewBridge.shouldCollapseIframeBySideEffect {
                let message = ["action": "flutter:navigateFinished"]
                webViewBridge.sendRequestWithCoroutine(message: message, parts: ["flutter", "navigateFinished"])
            }
        }
    }
    
    func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
        if webView == hostWebView {
            webViewBridge.readLanguageFromLocalStorage()
            
            hideSplashScreen()
            
            if webViewBridge.shouldRunScriptOnNextFinish {
                let msgToHost = [
                    "action": "host:onPageFinished",
                    "data": ["data": webView.url?.absoluteString ?? ""]
                ]
                let msgToFlutter = [
                    "action": "flutter:onHostPageFinished", 
                    "data": ["data": webView.url?.absoluteString ?? ""]
                ]
                
                webViewBridge.sendRequestWithCoroutine(message: msgToHost, parts: ["host", "onPageFinished"])
                webViewBridge.sendRequestWithCoroutine(message: msgToFlutter, parts: ["flutter", "onHostPageFinished"])
            }
        }
    }
    
    func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
        
        if webView == flutterWebView {
            guard let url = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }
            
            let scheme = url.scheme?.lowercased() ?? ""
            let host = url.host?.lowercased() ?? ""
            let isHttp = scheme == "http" || scheme == "https"
            
            if scheme == "intent" {
                decisionHandler(.cancel)
                return
            }
            
            let externalSchemes = Set(["tel", "mailto", "geo", "maps", "market"])
            if externalSchemes.contains(scheme) {
                UIApplication.shared.open(url, options: [:], completionHandler: nil)
                decisionHandler(.cancel)
                return
            }
            
            let isGoogleMaps = host.contains("maps.app.goo.gl") || 
                              host.contains("goo.gl") ||
                              (host.contains("google.com") && url.path.hasPrefix("/maps")) ||
                              host == "maps.google.com"
            
            if isHttp && isGoogleMaps {
                UIApplication.shared.open(url, options: [:], completionHandler: nil)
                decisionHandler(.cancel)
                return
            }
        }
        
        decisionHandler(.allow)
    }
}
