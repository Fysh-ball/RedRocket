package site.fysh.redrocket.service

import site.fysh.redrocket.queue.MessageTask

interface SmsProvider {
    suspend fun send(task: MessageTask): Boolean
}
