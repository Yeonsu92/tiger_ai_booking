import UIKit

class UIUtils {
    
    static func dpToPx(_ dp: CGFloat) -> CGFloat {
        return dp * UIScreen.main.scale
    }
    
    static func pxToDp(_ px: CGFloat) -> CGFloat {
        return px / UIScreen.main.scale
    }
    
    static func pointsToPixels(_ points: CGFloat) -> CGFloat {
        return points * UIScreen.main.scale
    }
    
    static func pixelsToPoints(_ pixels: CGFloat) -> CGFloat {
        return pixels / UIScreen.main.scale
    }
}
