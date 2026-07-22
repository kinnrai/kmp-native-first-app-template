package com.example.kmpnativefirst

import kotlin.js.JsExport

@JsExport
fun greet(): String = Greeting().greet()
