import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import { useConversationStore } from './stores/conversationStore'
import { savePersistedState } from './stores/conversationPersistence'

const pinia = createPinia()
const app = createApp(App).use(pinia)

const conversationStore = useConversationStore(pinia)
conversationStore.$subscribe((_mutation, state) => {
  savePersistedState({
    conversations: state.conversations,
    activeConversationId: state.activeConversationId,
  })
})

app.mount('#app')
