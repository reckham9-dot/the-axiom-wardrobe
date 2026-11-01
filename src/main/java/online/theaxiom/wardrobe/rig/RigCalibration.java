package online.theaxiom.wardrobe.rig;

/**
 * Reproducible, bounded calibration parameters for one experimental item model.
 * This is deliberately independent of Bukkit and never mutates game state.
 */
public final class RigCalibration {
    private RigCalibration() {}
    public record Values(float x, float y, float z, float scale) {
        public Values {
            if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z) || !Float.isFinite(scale))
                throw new IllegalArgumentException("Calibration values must be finite");
            if (Math.abs(x) > 1.5 || Math.abs(y) > 1.5 || Math.abs(z) > 1.5)
                throw new IllegalArgumentException("Offsets must be within [-1.5, 1.5]");
            if (scale < .05 || scale > 1.5)
                throw new IllegalArgumentException("Scale must be within [0.05, 1.5]");
        }
    }
    public static Values of(double x, double y, double z, double scale) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z) || !Double.isFinite(scale))
            throw new IllegalArgumentException("Calibration values must be finite");
        return new Values((float)x, (float)y, (float)z, (float)scale);
    }
    /** Strict parsing. Never silently clamp a potentially erroneous operator input. */
    public static Values parse(String[] args, int first) {
        if (args.length != first + 4) throw new IllegalArgumentException("Need exactly x y z scale");
        try {
            return of(Double.parseDouble(args[first]), Double.parseDouble(args[first+1]),
                Double.parseDouble(args[first+2]), Double.parseDouble(args[first+3]));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Calibration must contain four numbers",ex);
        }
    }
}
