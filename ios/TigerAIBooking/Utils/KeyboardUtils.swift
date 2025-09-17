import UIKit

class KeyboardUtils {
    
    static func setupKeyboardHandling(for viewController: ViewController) {
        NotificationCenter.default.addObserver(
            forName: UIResponder.keyboardWillShowNotification,
            object: nil,
            queue: .main
        ) { notification in
            handleKeyboardWillShow(notification: notification, viewController: viewController)
        }
        
        NotificationCenter.default.addObserver(
            forName: UIResponder.keyboardWillHideNotification,
            object: nil,
            queue: .main
        ) { notification in
            handleKeyboardWillHide(notification: notification, viewController: viewController)
        }
    }
    
    private static func handleKeyboardWillShow(notification: Notification, viewController: ViewController) {
        guard let keyboardFrame = notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
        
        let keyboardHeight = keyboardFrame.height
        let safeAreaBottom = viewController.view.safeAreaInsets.bottom
        let adjustedHeight = keyboardHeight - safeAreaBottom
        
        if let flutterWebView = viewController.view.subviews.compactMap({ $0 as? WKWebView }).last {
            if flutterWebView.frame.width == viewController.view.bounds.width {
                UIView.animate(withDuration: 0.3) {
                    flutterWebView.transform = CGAffineTransform(translationX: 0, y: -adjustedHeight)
                }
            }
        }
    }
    
    private static func handleKeyboardWillHide(notification: Notification, viewController: ViewController) {
        if let flutterWebView = viewController.view.subviews.compactMap({ $0 as? WKWebView }).last {
            UIView.animate(withDuration: 0.3) {
                flutterWebView.transform = .identity
            }
        }
    }
    
    static func removeKeyboardObservers() {
        NotificationCenter.default.removeObserver(self, name: UIResponder.keyboardWillShowNotification, object: nil)
        NotificationCenter.default.removeObserver(self, name: UIResponder.keyboardWillHideNotification, object: nil)
    }
}
