package com.example.musicapp.repository

import android.content.Context
import com.example.musicapp.R
import com.example.musicapp.Track
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.BufferedReader
import java.io.InputStream
import java.lang.reflect.Type

class MusicRepository constructor(var context: Context) {

    private var listOfTracks: MutableList<Track> = mutableListOf()

    private fun getListOfTracks(context: Context): List<Track> {

        var ins: InputStream = context.resources.openRawResource(R.raw.playlist)
        val listOfTracksStringJSON: String = ins.bufferedReader().use(BufferedReader::readText)

        val type: Type = Types.newParameterizedType(
            MutableList::class.java,
            Track::class.java
        )
        val moshi = Moshi.Builder().build()
        val trackAdapter: JsonAdapter<List<Track>> = moshi.adapter(type)
        return trackAdapter.fromJson(listOfTracksStringJSON)!!
    }

    private fun setListOfTrackIfEmptyList() {
        if (listOfTracks.isEmpty()) {
            listOfTracks.addAll(getListOfTracks(context))
        }
    }

    private var currentItemIndex = 0

    fun getNext(): Track {
        setListOfTrackIfEmptyList()
        if (currentItemIndex == listOfTracks.size - 1)
            currentItemIndex = 0
        else
            currentItemIndex++
        return getCurrent()
    }

    fun getPrevious(): Track {
        setListOfTrackIfEmptyList()
        if (currentItemIndex == 0)
            currentItemIndex = listOfTracks.size - 1
        else
            currentItemIndex--
        return getCurrent()
    }

    fun getCurrent(): Track {
        setListOfTrackIfEmptyList()
        return listOfTracks.get(currentItemIndex)
    }
}
