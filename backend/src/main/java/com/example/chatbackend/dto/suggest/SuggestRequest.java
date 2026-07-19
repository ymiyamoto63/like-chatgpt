package com.example.chatbackend.dto.suggest;

// 空文字・空白のみの入力も「候補なし」として200で返すため、ChatRequestと異なり@NotBlankは付けない
public record SuggestRequest(String text) {
}
