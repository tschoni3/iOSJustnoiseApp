//
//  AccountDeletionService.swift
//  justnoise
//
//  Created by TJ on 03.10.25.
//

import Foundation

enum AccountDeletionError: LocalizedError {
    case noToken, http(Int), message(String)
    var errorDescription: String? {
        switch self {
        case .noToken: return "Missing access token."
        case .http(let c): return "Server responded with \(c)."
        case .message(let m): return m
        }
    }
}

struct AccountDeletionService {
    let functionURL: URL

    func deleteAccount(accessToken: String?) async throws {
        guard let token = accessToken, !token.isEmpty else { throw AccountDeletionError.noToken }

        var req = URLRequest(url: functionURL)
        req.httpMethod = "POST"
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")

        let (data, resp) = try await URLSession.shared.data(for: req)
        guard let http = resp as? HTTPURLResponse else {
            throw AccountDeletionError.message("No HTTP response")
        }
        guard http.statusCode == 200 else {
            if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let msg = obj["error"] as? String {
                throw AccountDeletionError.message(msg)
            }
            throw AccountDeletionError.http(http.statusCode)
        }
    }
}
