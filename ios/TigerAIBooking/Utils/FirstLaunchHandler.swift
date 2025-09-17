import Foundation

class FirstLaunchHandler {
    
    static func getOrCreateUUID() -> String {
        let userDefaults = UserDefaults.standard
        let key = "app_uuid"
        
        if let existingUUID = userDefaults.string(forKey: key) {
            return existingUUID.replacingOccurrences(of: "-", with: "")
        }
        
        let newUUID = UUID().uuidString
        userDefaults.set(newUUID, forKey: key)
        
        return newUUID.replacingOccurrences(of: "-", with: "")
    }
}
