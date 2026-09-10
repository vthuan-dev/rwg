package com.rwg.game.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

/**
 * Quyet dinh no Jackpot Xoc Dia Tu Quy.
 *
 * <p>PURE (khong Spring): nhan config dang tham so de de unit test.
 */
public final class XocDiaJackpotService {

    private XocDiaJackpotService() {
    }

    public record JackpotConfig(
            long pool,
            long threshold,
            String triggerMode,
            double autoRate,
            String targetDoor,
            String winMode,
            BigDecimal winValue) {
    }

    public record JackpotDecision(
            boolean hit,
            String door,
            int redCount,
            List<Integer> diceQuad,
            BigDecimal winAmount) {
    }

    public static final JackpotDecision NO_HIT =
            new JackpotDecision(false, null, -1, null, BigDecimal.ZERO);

    public static JackpotDecision decide(JackpotConfig cfg, Random random) {
        if (cfg == null || random == null) {
            return NO_HIT;
        }
        String mode = cfg.triggerMode() == null ? "AUTO" : cfg.triggerMode().trim().toUpperCase();
        if ("OFF".equals(mode)) {
            return NO_HIT;
        }
        if ("FORCE_NEXT_ROUND".equals(mode)) {
            String door = resolveDoor(cfg.targetDoor(), random);
            return hit(door, cfg);
        }
        if (cfg.pool() < cfg.threshold()) {
            return NO_HIT;
        }
        if (cfg.autoRate() <= 0 || random.nextDouble() >= cfg.autoRate()) {
            return NO_HIT;
        }
        String door = resolveDoor(cfg.targetDoor(), random);
        return hit(door, cfg);
    }

    private static JackpotDecision hit(String door, JackpotConfig cfg) {
        int redCount = redCountForDoor(door);
        List<Integer> diceQuad = diceQuadForDoor(door);
        BigDecimal amount = winAmount(cfg.pool(), cfg.winMode(), cfg.winValue());
        return new JackpotDecision(true, door, redCount, diceQuad, amount);
    }

    static String resolveDoor(String targetDoor, Random random) {
        if (targetDoor == null) {
            return randomDoor(random);
        }
        String d = targetDoor.trim().toUpperCase();
        return switch (d) {
            case "CHAN", "EVEN", "FOUR_RED", "FOUR_WHITE", "THREE_RED", "THREE_WHITE", "LE", "ODD" ->
                    normalizeDoor(d);
            default -> randomDoor(random);
        };
    }

    private static String normalizeDoor(String d) {
        return switch (d) {
            case "EVEN" -> "CHAN";
            case "ODD" -> "LE";
            default -> d;
        };
    }

    private static String randomDoor(Random random) {
        String[] doors = {"CHAN", "LE", "FOUR_RED", "FOUR_WHITE", "THREE_RED", "THREE_WHITE"};
        return doors[random.nextInt(doors.length)];
    }

    public static int redCountForDoor(String door) {
        if (door == null) {
            return 2;
        }
        return switch (door.trim().toUpperCase()) {
            case "CHAN" -> 2;
            case "LE" -> 1;
            case "FOUR_RED" -> 4;
            case "FOUR_WHITE" -> 0;
            case "THREE_RED" -> 3;
            case "THREE_WHITE" -> 1;
            default -> 2;
        };
    }

    public static List<Integer> diceQuadForDoor(String door) {
        int face = switch (door == null ? "" : door.trim().toUpperCase()) {
            case "LE" -> 1;
            case "FOUR_RED" -> 2;
            case "THREE_WHITE" -> 3;
            case "FOUR_WHITE" -> 4;
            case "THREE_RED" -> 5;
            default -> 6;
        };
        return List.of(face, face, face, face);
    }

    public static BigDecimal winAmount(long pool, String winMode, BigDecimal winValue) {
        if (pool <= 0) {
            return BigDecimal.ZERO;
        }
        String m = winMode == null ? "FULL_POOL" : winMode.trim().toUpperCase();
        return switch (m) {
            case "PERCENT" -> {
                BigDecimal pct = winValue == null ? new BigDecimal("100") : winValue;
                if (pct.compareTo(BigDecimal.ZERO) <= 0) yield BigDecimal.ZERO;
                if (pct.compareTo(new BigDecimal("100")) > 0) pct = new BigDecimal("100");
                yield BigDecimal.valueOf(pool).multiply(pct)
                        .divide(new BigDecimal("100"), 0, RoundingMode.DOWN);
            }
            case "FIXED" -> {
                BigDecimal fixed = winValue == null ? BigDecimal.valueOf(pool) : winValue;
                if (fixed.compareTo(BigDecimal.ZERO) <= 0) yield BigDecimal.ZERO;
                yield fixed.min(BigDecimal.valueOf(pool));
            }
            default -> BigDecimal.valueOf(pool);
        };
    }
}
