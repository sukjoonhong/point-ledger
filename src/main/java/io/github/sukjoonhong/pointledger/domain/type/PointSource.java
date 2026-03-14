package io.github.sukjoonhong.pointledger.domain.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Arrays;

@Getter
@RequiredArgsConstructor
public enum PointSource {
    ADMIN(100, "관리자 수동 지급"),   // 최우선 차감
    SYSTEM(200, "시스템 보상"),      // 2순위
    ORDER(300, "주문/결제 적립"),     // 3순위
    EVENT(400, "이벤트 참여");       // 4순위

    private final int priority;
    private final String description;

    public static PointSource fromPriority(int priority) {
        return Arrays.stream(values())
                .filter(s -> s.priority == priority)
                .findFirst()
                .orElseThrow(() -> {
                    return new IllegalArgumentException("Invalid PointSource priority: " + priority);
                });
    }
}