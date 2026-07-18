package com.example.chatbackend;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
		@JsonSubTypes.Type(value = TableComponent.class, name = "table"),
		@JsonSubTypes.Type(value = BarChartComponent.class, name = "bar_chart"),
		@JsonSubTypes.Type(value = ChoicesComponent.class, name = "choices"),
		@JsonSubTypes.Type(value = TrendChartComponent.class, name = "trend_chart")
})
public sealed interface UiComponent permits TableComponent, BarChartComponent, ChoicesComponent, TrendChartComponent {
}
