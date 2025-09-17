# Tiger AI Booking - iOS

This is the iOS version of the Tiger AI Booking app, converted from the Android webview bridge implementation.

## Architecture

The iOS app maintains the same dual WebView architecture as the Android version:

- **Host WebView**: Loads the main Tiger Booking website (tigerbooking.golf)
- **Flutter WebView**: Loads the Flutter web app overlay that can expand/collapse
- **WebView Bridge**: Handles JavaScript-to-native communication using WKScriptMessageHandler

## Key Components

### ViewController.swift
Main view controller equivalent to Android's MainActivity. Manages:
- Two WKWebViews with proper constraints
- Splash screen display and hiding
- WebView expansion/collapse animations
- Navigation handling

### WebViewBridge.swift
JavaScript bridge equivalent to Android's FlutterBridge. Handles:
- Message passing between native iOS and JavaScript
- Server-side script injection via API calls
- Language synchronization between WebViews
- UUID management

### Utility Classes
- **KeyboardUtils**: Handles keyboard appearance and layout adjustments
- **FirstLaunchHandler**: UUID generation and persistence using UserDefaults
- **UIUtils**: Point/pixel conversion utilities
- **ApiResponse**: Codable struct for server API responses

## Features

All Android features have been converted to iOS equivalents:

✅ Dual WebView system with host and overlay
✅ JavaScript bridge communication (WKScriptMessageHandler)
✅ Server-side script injection
✅ Dynamic layout changes (60pt collapsed to full screen)
✅ Language synchronization between WebViews
✅ UUID generation and persistence (UserDefaults)
✅ Keyboard-aware layout adjustments
✅ External link handling
✅ Splash screen management

## iOS-Specific Implementations

- **WKWebView** instead of Android WebView for better performance and security
- **WKScriptMessageHandler** for JavaScript bridge instead of @JavascriptInterface
- **URLSession** for networking instead of OkHttp
- **Auto Layout constraints** instead of ConstraintLayout
- **UserDefaults** instead of SharedPreferences
- **NotificationCenter** for keyboard events instead of WindowInsetsAnimationCompat

## Requirements

- iOS 17.0+
- Xcode 15.0+
- Swift 5.0+

## Building

1. Open `TigerAIBooking.xcodeproj` in Xcode
2. Select your target device or simulator
3. Build and run the project

The app will load the Tiger Booking website in the host WebView and the Flutter overlay in the flutter WebView, maintaining all the bridge functionality from the Android version.
