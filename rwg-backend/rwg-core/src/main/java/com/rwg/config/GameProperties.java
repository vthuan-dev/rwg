package com.rwg.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Cấu hình vòng chơi game (rwg.game.round.*) — thời lượng từng pha
 * config-driven để test rút ngắn (docs/round-lifecycle.md).
 * Mặc định prod: BETTING_OPEN 45s, BETTING_CLOSED 2s, SPINNING 8s, RESULT 3s, SETTLE 5s.
 */
@ConfigurationProperties(prefix = "rwg.game")
public record GameProperties(Round round, Duration betPlacedWindow) {

    public GameProperties {
        if (round == null) {
            round = new Round(Duration.ofSeconds(45), Duration.ofSeconds(2),
                    Duration.ofSeconds(8), Duration.ofSeconds(3), Duration.ofSeconds(5));
        }
        if (betPlacedWindow == null) {
            betPlacedWindow = Duration.ofMillis(250);
        }
    }

    /** Thời lượng 5 pha của một vòng Roulette. */
    public record Round(Duration bettingOpen, Duration bettingClosed, Duration spinning,
                        Duration result, Duration settle) {

        public Round {
            if (bettingOpen == null) bettingOpen = Duration.ofSeconds(45);
            if (bettingClosed == null) bettingClosed = Duration.ofSeconds(2);
            if (spinning == null) spinning = Duration.ofSeconds(8);
            if (result == null) result = Duration.ofSeconds(3);
            if (settle == null) settle = Duration.ofSeconds(5);
        }
    }
}
