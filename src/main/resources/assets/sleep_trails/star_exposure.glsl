// Sleep Trails: integrate the pack's ORIGINAL star function; its hash, density, color and attenuation stay there.
// Coordinates are measured in the pack's quantized cells (0.2 * 1024 per star coordinate).
uniform float sleeptrailsExposureTicks;
uniform int sleeptrailsClockMod;

vec3 SleepTrailsCellIntegral(vec2 endpoint, float cells, float VdotU, float VdotS) {
    if (cells <= 0.0) return vec3(0.0);
    float hi = endpoint.x * 0.2 * 1024.0;
    float lo = hi - cells;
    float first = floor(lo);
    vec3 energy = vec3(0.0);
    // At most ceil(1024 * 0.0003 * 204.8) + 1 intersected cells. The bound never truncates the interval.
    for (int j = 0; j < 65; j++) {
        float cell = first + float(j);
        if (cell >= hi) break;
        float weight = max(0.0, min(hi, cell + 1.0) - max(lo, cell));
        vec2 at = vec2((cell + 0.5) / (0.2 * 1024.0), endpoint.y);
        energy += SleepTrailsInstantStars(at, VdotU, VdotS) * weight;
    }
    return energy;
}

vec3 SleepTrailsReferenceIntegral(vec2 endpoint, float ticks, float VdotU, float VdotS) {
    vec3 sum = vec3(0.0);
    // Only compiled into the explicit private reference fixture. Shipping integration uses the cell integral.
    #ifdef SLEEPTRAILS_REFERENCE
        for (int j = 0; j < 4096; j++) {
            float age = ticks * (float(j) + 0.5) / 4096.0;
            float x = endpoint.x - age * 0.0003;
            if (age > float(sleeptrailsClockMod)) x += 720.0;
            sum += SleepTrailsInstantStars(vec2(x, endpoint.y), VdotU, VdotS);
        }
        sum *= (ticks * 0.0003 * 0.2 * 1024.0) / 4096.0;
    #endif
    return sum;
}

vec3 GetStars(vec2 starCoord, float VdotU, float VdotS) {
    vec3 instant = SleepTrailsInstantStars(starCoord, VdotU, VdotS);
    if (sleeptrailsExposureTicks <= 0.0 || VdotU < 0.0) return instant;
    #if NIGHT_STAR_AMOUNT == 0
        return instant;
    #endif
    float ticks = clamp(sleeptrailsExposureTicks, 0.0, 1024.0);
    vec3 energy;
    #ifdef SLEEPTRAILS_REFERENCE
        energy = SleepTrailsReferenceIntegral(starCoord, ticks, VdotU, VdotS);
    #else
        float recent = min(ticks, float(sleeptrailsClockMod));
        energy = SleepTrailsCellIntegral(starCoord, recent * 0.0003 * 0.2 * 1024.0, VdotU, VdotS);
        if (recent < ticks) {
            // The original pack resets syncedTime after day 99. Integrate its two real fields, never a bridge.
            vec2 previousEnd = vec2(starCoord.x - recent * 0.0003 + 720.0, starCoord.y);
            energy += SleepTrailsCellIntegral(previousEnd, (ticks - recent) * 0.0003 * 0.2 * 1024.0, VdotU, VdotS);
        }
    #endif
    // One-cell dwell normalization preserves the intensity of a single star crossing. Keep current leading stars.
    return max(instant, energy);
}
