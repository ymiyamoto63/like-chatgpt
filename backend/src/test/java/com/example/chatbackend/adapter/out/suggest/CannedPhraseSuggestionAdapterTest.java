package com.example.chatbackend.adapter.out.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class CannedPhraseSuggestionAdapterTest {

	private final CannedPhraseSuggestionAdapter service = new CannedPhraseSuggestionAdapter();

	@Test
	void suggestReturnsRemainderForPrefixOfAssigneePhrase() {
		assertEquals("当者別問い合わせ", service.generateSuggestion("当月担").completion());
	}

	@Test
	void suggestReturnsFirstMatchInDefinitionOrderForSharedPrefix() {
		// 「当月」は担当者別・カテゴリ別の両方に前方一致するが、定義順の先頭を返す
		assertEquals("担当者別問い合わせ", service.generateSuggestion("当月").completion());
	}

	@Test
	void suggestReturnsRemainderForPrefixOfDailyPhrase() {
		assertEquals("14日間の日別問い合わせ", service.generateSuggestion("直近").completion());
	}

	@Test
	void suggestReturnsEmptyForNonMatchingText() {
		assertNull(service.generateSuggestion("こんにちは").completion());
	}

	@Test
	void suggestReturnsEmptyForExactMatch() {
		assertNull(service.generateSuggestion("当月担当者別問い合わせ").completion());
	}

	@Test
	void suggestReturnsEmptyForEmptyText() {
		assertNull(service.generateSuggestion("").completion());
	}

	@Test
	void suggestReturnsEmptyForNullText() {
		assertNull(service.generateSuggestion(null).completion());
	}

}
