package com.mach1.ar.heading_example

//
//  Event.kt
//  BoseWearable
//
//  Created by Tambet Ingo on 02/19/2019.
//  Copyright © 2019 Bose Corporation. All rights reserved.
//

class Event<T>(private val content: T) {
    private var handled = false

    fun peek(): T {
        return content
    }

    fun get(): T? {
        if (handled) {
            return null
        }
        handled = true
        return content
    }
}