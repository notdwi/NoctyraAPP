package com.noctyra.app.ui.screens.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

interface PlayerActions {
    fun onPrevious()
    fun onNext()
    fun onUserPlayPause(playing: Boolean, positionMs: Long)
    fun onUserSeek(positionMs: Long)
}

/**
 * Entregue ao PlayerView no lugar do ExoPlayer: só o que o usuário faz pelos controles passa por aqui.
 * A sincronia da party mexe direto no ExoPlayer, então nunca é confundida com uma ação do usuário.
 */
@OptIn(UnstableApi::class)
class ControlPlayer(
    player: Player,
    private val hasPrevious: Boolean,
    private val hasNext: Boolean,
    private val actions: PlayerActions
) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .removeAll(
                Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
            )
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS, hasPrevious)
            .addIf(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM, hasPrevious)
            .addIf(Player.COMMAND_SEEK_TO_NEXT, hasNext)
            .addIf(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, hasNext)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = when (command) {
        Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> hasPrevious
        Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> hasNext
        else -> super.isCommandAvailable(command)
    }

    override fun seekToPrevious() = actions.onPrevious()
    override fun seekToPreviousMediaItem() = actions.onPrevious()
    override fun seekToNext() = actions.onNext()
    override fun seekToNextMediaItem() = actions.onNext()

    override fun play() {
        super.play()
        actions.onUserPlayPause(true, currentPosition)
    }

    override fun pause() {
        super.pause()
        actions.onUserPlayPause(false, currentPosition)
    }

    override fun seekTo(positionMs: Long) {
        super.seekTo(positionMs)
        actions.onUserSeek(positionMs)
    }

    override fun seekTo(mediaItemIndex: Int, positionMs: Long) {
        super.seekTo(mediaItemIndex, positionMs)
        actions.onUserSeek(positionMs)
    }

    override fun seekBack() {
        super.seekBack()
        actions.onUserSeek(currentPosition)
    }

    override fun seekForward() {
        super.seekForward()
        actions.onUserSeek(currentPosition)
    }
}
