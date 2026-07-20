package com.example.chatbackend.application.port.out;

import com.example.chatbackend.domain.faq.FaqEntry;

import java.util.List;
import java.util.Optional;

public interface FaqQueryPort {

	Optional<FaqEntry> findByTitle(String title);

	List<String> findTitlesByCategory(String category);

}
