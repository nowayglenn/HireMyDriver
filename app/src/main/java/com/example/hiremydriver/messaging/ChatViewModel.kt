package com.example.hiremydriver.messaging

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

class ChatViewModel(repository: ChatRepository, conversationId: String) : ViewModel() {
    val state = MutableLiveData(ChatState())
    var draft = ""
    val session = ChatSession(repository, conversationId, history = ChatHistoryCache.shared, changed = {
        if (state.value?.userId != it.userId) draft = ""
        state.value = it
    })
    override fun onCleared() { session.close() }

    class Factory(private val repository: ChatRepository, private val id: String) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ChatViewModel(repository, id) as T
    }
}
