//
//  Schedule.swift
//  

import Foundation

typealias Weekday = Int

struct Schedule: Identifiable, Codable, Equatable {
    var id: UUID = UUID()
    var name: String
    var modeId: UUID
    var date: Date
    var repeatWeekdays: [Weekday] = []
    var isEnabled: Bool = true
    var lastFireDate: Date? = nil
}
