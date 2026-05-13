package dev.seedo.user.domain;

/**
 * 사용자 성별. V9 의 {@code users_gender_check} CHECK 와 짝.
 * UNDISCLOSED 는 명시적 "공개하지 않음" — NULL (미입력) 과 구분.
 */
public enum Gender {
    MALE,
    FEMALE,
    OTHER,
    UNDISCLOSED
}
