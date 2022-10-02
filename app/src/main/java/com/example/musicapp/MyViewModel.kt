package com.example.musicapp

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MyViewModel : ViewModel() {
    val tracks = MutableLiveData<Track>()
    val states = MutableLiveData<Int>()
}