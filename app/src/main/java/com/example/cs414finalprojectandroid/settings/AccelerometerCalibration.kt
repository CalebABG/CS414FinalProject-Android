package com.example.cs414finalprojectandroid.settings

data class AccelerometerCalibration(
    var minX: Int = 0,
    var maxX: Int = 0,
    var minY: Int = 0,
    var maxY: Int = 0,
    var biasX: Int = 0,
    var biasY: Int = 0
)
