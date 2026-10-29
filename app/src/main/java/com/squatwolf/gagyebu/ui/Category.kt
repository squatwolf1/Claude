package com.squatwolf.gagyebu.ui

import com.squatwolf.gagyebu.data.Entry

/** 분류마다 한눈에 알아보게 붙이는 그림 글자. */
fun emojiOf(category: String) = when (category) {
    "식비" -> "🍚"
    "카페" -> "☕"
    "교통" -> "🚌"
    "쇼핑" -> "🛍️"
    "생활" -> "🏠"
    "공과금" -> "🧾"
    "대출" -> "🏦"
    "보험" -> "🛡️"
    "유류" -> "⛽"
    "병원" -> "🏥"
    "저금" -> "🐷"
    "부동저금", "저금(사용불가)" -> "🔒"
    "유동저금", "저금(사용가능)" -> "💧"
    "의료" -> "💊"
    "문화" -> "🎬"
    "월급" -> "💰"
    "용돈" -> "🎁"
    "부수입" -> "💵"
    else -> "📌"
}

/** 고정비로 보는 분류. 매달 고정 내역도 고정비다. */
val fixedCategories = setOf("공과금", "대출", "보험")

fun Entry.isFixedCost() = !income && (ruleId.isNotEmpty() || category in fixedCategories)

/** 매달 고정으로 들어오는 수입(월급 등). */
fun Entry.isFixedIncome() = income && ruleId.isNotEmpty()
