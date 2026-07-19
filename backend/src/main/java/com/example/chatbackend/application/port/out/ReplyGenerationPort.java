package com.example.chatbackend.application.port.out;

import com.example.chatbackend.domain.chat.ChatResponse;

public interface ReplyGenerationPort {

	ChatResponse generateReply(String message);

}
