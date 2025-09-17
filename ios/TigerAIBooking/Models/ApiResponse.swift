import Foundation

struct ApiResponse: Codable {
    let result: Int
    let resultMessage: String
    let javascriptCode: String
}
