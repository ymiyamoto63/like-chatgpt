package com.example.chatbackend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class MockSuggestServiceTest {

	private final MockSuggestService service = new MockSuggestService();

	@Test
	void suggestReturnsRemainderForPrefixOfAssigneePhrase() {
		assertEquals("当者別問い合わせ", service.suggest("当月担").completion());
	}

	@Test
	void suggestReturnsFirstMatchInDefinitionOrderForSharedPrefix() {
		// 「当月」は担当者別・カテゴリ別の両方に前方一致するが、定義順の先頭を返す
		assertEquals("担当者別問い合わせ", service.suggest("当月").completion());
	}

	@Test
	void suggestReturnsRemainderForPrefixOfDailyPhrase() {
		assertEquals("14日間の日別問い合わせ", service.suggest("直近").completion());
	}

	@Test
	void suggestReturnsEmptyForNonMatchingText() {
		assertNull(service.suggest("こんにちは").completion());
	}

	@Test
	void suggestReturnsEmptyForExactMatch() {
		assertNull(service.suggest("当月担当者別問い合わせ").completion());
	}

	@Test
	void suggestReturnsEmptyForEmptyText() {
		assertNull(service.suggest("").completion());
	}

	@Test
	void suggestReturnsEmptyForNullText() {
		assertNull(service.suggest(null).completion());
	}

}
