package lightmods;

/** Прогресс длительной операции: доля 0..1 и текущее действие. */
public interface Progress {
    Progress NONE = (f, t) -> {
    };

    void set(double fraction, String text);

    /** Под-диапазон: внутренняя доля 0..1 отображается в [base, base+span]. */
    default Progress sub(double base, double span) {
        return (f, t) -> set(base + span * Math.max(0, Math.min(1, f)), t);
    }
}
