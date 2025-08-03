/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.pandora

import android.content.Intent
import android.media.MediaPlayer
import android.os.Bundle
import android.support.v4.media.*
import android.support.v4.media.MediaBrowserCompat.MediaItem
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.*
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_ALL
import android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_GROUP
import android.support.v4.media.session.PlaybackStateCompat.SHUFFLE_MODE_NONE
import android.util.Log
import androidx.media.MediaBrowserServiceCompat
import androidx.media.MediaBrowserServiceCompat.BrowserRoot

/* MediaBrowserService to handle MediaButton and Browsing */
class MediaPlayerBrowserService : MediaBrowserServiceCompat() {
    private val TAG = "PandoraMediaPlayerBrowserService"

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var playbackStateBuilder: PlaybackStateCompat.Builder
    private var mMediaPlayer: MediaPlayer? = null
    private val mediaIdToChildren = mutableMapOf<String, MutableList<MediaItem>>()
    private var metadataItems = mutableMapOf<String, MediaMetadataCompat>()
    private var queue = mutableListOf<MediaSessionCompat.QueueItem>()
    private var currentTrack = -1

    override fun onCreate() {
        super.onCreate()
        initBrowseFolderList()
        setupMediaSession()
        instance = this
    }

    private fun setupMediaSession() {
        mediaSession = MediaSessionCompat(this, "MediaSession")
        mediaSession.setCallback(mSessionCallback)
        initQueue()
        mediaSession.setQueue(queue)
        playbackStateBuilder =
            PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_NONE, 0, 1.0f)
                .setActions(getAvailableActions())
                .setActiveQueueItemId(QUEUE_START_INDEX.toLong())
        mediaSession.setPlaybackState(playbackStateBuilder.build())
        mediaSession.setMetadata(null)
        mediaSession.setQueueTitle(NOW_PLAYING_PREFIX)
        mediaSession.setActive(true)
        setSessionToken(mediaSession.getSessionToken())
    }

    private fun getAvailableActions(): Long =
        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_REWIND or
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_STOP or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE

    private fun setPlaybackState(state: Int) {
        playbackStateBuilder.setState(state, 0, 1.0f).setActiveQueueItemId(currentTrack.toLong())
        mediaSession.setPlaybackState(playbackStateBuilder.build())
    }

    fun startTestPlayback() {
        if (mMediaPlayer == null) {
            // File copied from: development/samples/ApiDemos/res/raw/test_cbr.mp3
            // to: packages/modules/Bluetooth/android/pandora/server/res/raw/test_cbr.mp3
            val resourceId: Int = getResources().getIdentifier("test_cbr", "raw", getPackageName())
            mMediaPlayer = MediaPlayer.create(this, resourceId)
            if (mMediaPlayer == null) {
                Log.e(TAG, "Failed to create MediaPlayer.")
                return
            }
        }

        mMediaPlayer?.setOnCompletionListener { stopTestPlayback() }

        mMediaPlayer?.start()
    }

    fun stopTestPlayback() {
        mMediaPlayer?.stop()
        mMediaPlayer?.setOnCompletionListener(null)
        mMediaPlayer?.release()
        mMediaPlayer = null
    }

    fun play() {
        if (currentTrack == -1 || currentTrack == QUEUE_SIZE) currentTrack = QUEUE_START_INDEX
        else currentTrack += 1
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
        mediaSession.setMetadata(metadataItems.get("" + currentTrack))
    }

    fun stop() {
        setPlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSession.setMetadata(null)
    }

    fun pause() {
        setPlaybackState(PlaybackStateCompat.STATE_PAUSED)
    }

    fun rewind() {
        setPlaybackState(PlaybackStateCompat.STATE_REWINDING)
    }

    fun fastForward() {
        setPlaybackState(PlaybackStateCompat.STATE_FAST_FORWARDING)
    }

    fun forward() {
        if (currentTrack == QUEUE_SIZE || currentTrack == -1) currentTrack = QUEUE_START_INDEX
        else currentTrack += 1
        setPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_NEXT)
        mediaSession.setMetadata(metadataItems.get("" + currentTrack))
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    fun backward() {
        if (currentTrack == QUEUE_START_INDEX || currentTrack == -1) currentTrack = QUEUE_SIZE
        else currentTrack -= 1
        setPlaybackState(PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS)
        mediaSession.setMetadata(metadataItems.get("" + currentTrack))
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    fun setLargeMetadata() {
        currentTrack = QUEUE_SIZE
        mediaSession.setMetadata(metadataItems.get("" + currentTrack))
        setPlaybackState(PlaybackStateCompat.STATE_PLAYING)
    }

    fun updateQueue() {
        val metaData: MediaMetadataCompat =
            MediaMetadataCompat.Builder()
                .putString(
                    MediaMetadataCompat.METADATA_KEY_MEDIA_ID,
                    NOW_PLAYING_PREFIX + NEW_QUEUE_ITEM_INDEX
                )
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Title" + NEW_QUEUE_ITEM_INDEX)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, "Artist" + NEW_QUEUE_ITEM_INDEX)
                .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Album" + NEW_QUEUE_ITEM_INDEX)
                .putLong(
                    MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER,
                    NEW_QUEUE_ITEM_INDEX.toLong()
                )
                .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, NEW_QUEUE_ITEM_INDEX.toLong())
                .build()
        val mediaItem = MediaItem(metaData.description, MediaItem.FLAG_PLAYABLE)
        queue.add(
            MediaSessionCompat.QueueItem(mediaItem.description, NEW_QUEUE_ITEM_INDEX.toLong())
        )
        mediaSession.setQueue(queue)
    }

    fun getShuffleMode(): Int {
        val controller = mediaSession.getController()
        return controller.getShuffleMode()
    }

    fun setShuffleMode(shuffleMode: Int) {
        val controller = mediaSession.getController()
        val transportControls = controller.getTransportControls()
        when (shuffleMode) {
            SHUFFLE_MODE_NONE,
            SHUFFLE_MODE_ALL,
            SHUFFLE_MODE_GROUP -> transportControls.setShuffleMode(shuffleMode)
            else -> transportControls.setShuffleMode(SHUFFLE_MODE_NONE)
        }
    }

    private val mSessionCallback: MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                Log.i(TAG, "onPlay")
                play()
            }

            override fun onPause() {
                Log.i(TAG, "onPause")
                pause()
            }

            override fun onSkipToPrevious() {
                Log.i(TAG, "onSkipToPrevious")
                // TODO : Need to handle to play previous audio in the list
            }

            override fun onSkipToNext() {
                Log.i(TAG, "onSkipToNext")
                // TODO : Need to handle to play next audio in the list
            }

            override fun onMediaButtonEvent(mediaButtonEvent: Intent): Boolean {
                Log.i(TAG, "MediaSessionCallback——》onMediaButtonEvent $mediaButtonEvent")
                return super.onMediaButtonEvent(mediaButtonEvent)
            }

            override fun onSetShuffleMode(shuffleMode: Int) {
                Log.i(TAG, "MediaSessionCallback——》onSetShuffleMode $shuffleMode")
                mediaSession.setShuffleMode(shuffleMode)
            }
        }

    override fun onGetRoot(p0: String, clientUid: Int, rootHints: Bundle?): BrowserRoot? {
        Log.i(TAG, "onGetRoot")
        return BrowserRoot(ROOT, null)
    }

    override fun onLoadChildren(parentId: String, result: Result<MutableList<MediaItem>>) {
        Log.i(TAG, "onLoadChildren")
        if (parentId == ROOT) {
            val map = mediaIdToChildren[ROOT]
            Log.i(TAG, "onloadchildren $map")
            result.sendResult(map)
        } else if (parentId == NOW_PLAYING_PREFIX) {
            result.sendResult(mediaIdToChildren[NOW_PLAYING_PREFIX])
        } else {
            Log.i(TAG, "onloadchildren inside else")
            result.sendResult(null)
        }
    }

    private fun initMediaItems() {
        var mediaItems = mutableListOf<MediaItem>()
        for (item in QUEUE_START_INDEX..QUEUE_SIZE) {
            val metaData: MediaMetadataCompat =
                MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, NOW_PLAYING_PREFIX + item)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, "Title$item")
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_ARTIST,
                        if (item != QUEUE_SIZE) "Artist$item" else generateAlphanumericString(512)
                    )
                    .putString(
                        MediaMetadataCompat.METADATA_KEY_ALBUM,
                        if (item != QUEUE_SIZE) "Album$item" else generateAlphanumericString(512)
                    )
                    .putLong(MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER, item.toLong())
                    .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, QUEUE_SIZE.toLong())
                    .build()
            val mediaItem = MediaItem(metaData.description, MediaItem.FLAG_PLAYABLE)
            mediaItems.add(mediaItem)
            metadataItems.put("" + item, metaData)
        }
        mediaIdToChildren[NOW_PLAYING_PREFIX] = mediaItems
    }

    private fun initQueue() {
        for ((key, value) in metadataItems.entries) {
            val mediaItem = MediaItem(value.description, MediaItem.FLAG_PLAYABLE)
            queue.add(MediaSessionCompat.QueueItem(mediaItem.description, key.toLong()))
        }
    }

    private fun initBrowseFolderList() {
        var rootList = mediaIdToChildren[ROOT] ?: mutableListOf()

        val emptyFolderMetaData =
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, EMPTY_FOLDER)
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, EMPTY_FOLDER)
                .putLong(
                    MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE,
                    MediaDescriptionCompat.BT_FOLDER_TYPE_PLAYLISTS
                )
                .build()
        val emptyFolderMediaItem =
            MediaItem(emptyFolderMetaData.description, MediaItem.FLAG_BROWSABLE)

        val playlistMetaData =
            MediaMetadataCompat.Builder()
                .apply {
                    putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, NOW_PLAYING_PREFIX)
                    putString(MediaMetadataCompat.METADATA_KEY_TITLE, NOW_PLAYING_PREFIX)
                    putLong(
                        MediaMetadataCompat.METADATA_KEY_BT_FOLDER_TYPE,
                        MediaDescriptionCompat.BT_FOLDER_TYPE_PLAYLISTS
                    )
                }
                .build()

        val playlistsMediaItem = MediaItem(playlistMetaData.description, MediaItem.FLAG_BROWSABLE)

        rootList += emptyFolderMediaItem
        rootList += playlistsMediaItem
        mediaIdToChildren[ROOT] = rootList
        initMediaItems()
    }

    companion object {
        lateinit var instance: MediaPlayerBrowserService
        const val ROOT = "__ROOT__"
        const val EMPTY_FOLDER = "@empty@"
        const val NOW_PLAYING_PREFIX = "NowPlayingId"
        const val QUEUE_START_INDEX = 1
        const val QUEUE_SIZE = 6
        const val NEW_QUEUE_ITEM_INDEX = 7

        fun isInitialized(): Boolean = this::instance.isInitialized
    }
}
