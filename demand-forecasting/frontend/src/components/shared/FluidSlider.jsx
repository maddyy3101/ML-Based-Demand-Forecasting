import { useEffect, useMemo, useState } from "react";

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function formatValue(value) {
  if (!Number.isFinite(value)) return "0";
  return value.toLocaleString("en-IN");
}

function applySnap(value, min, max, snapStep) {
  const clamped = clamp(value, min, max);
  if (!snapStep || snapStep <= 0) return clamped;
  const snapped = Math.round((clamped - min) / snapStep) * snapStep + min;
  return clamp(snapped, min, max);
}

export default function FluidSlider({
  label,
  value,
  min,
  max,
  step = 1,
  snapStep,
  tickCount = 5,
  ticks,
  showTicks = true,
  showValueBubble = false,
  tickFormatter,
  unit,
  showInput = true,
  onChange,
}) {
  const numeric = applySnap(Number(value ?? min), min, max, snapStep);
  const denominator = Math.max(max - min, 1);
  const percent = ((numeric - min) / denominator) * 100;
  const [isDragging, setIsDragging] = useState(false);
  const [pulse, setPulse] = useState(false);

  useEffect(() => {
    setPulse(true);
    const timer = setTimeout(() => setPulse(false), 180);
    return () => clearTimeout(timer);
  }, [numeric]);

  const tickValues = useMemo(() => {
    if (Array.isArray(ticks) && ticks.length >= 2) {
      return ticks.map((v) => clamp(Number(v), min, max));
    }
    if (!showTicks) return [];
    const safeCount = Math.max(2, Number(tickCount || 5));
    return Array.from({ length: safeCount }, (_, index) => {
      const ratio = index / (safeCount - 1);
      const raw = min + (max - min) * ratio;
      return snapStep ? applySnap(raw, min, max, snapStep) : Math.round(raw);
    }).filter((value, index, arr) => arr.indexOf(value) === index);
  }, [ticks, showTicks, tickCount, min, max, snapStep]);

  const handleChange = (next) => {
    onChange(applySnap(Number(next), min, max, snapStep));
  };

  return (
    <div className="pg-slider-wrap">
      <div className="flex items-center justify-between text-sm mb-2">
        <span>{label}</span>
        <span className="font-semibold text-[var(--text-1)]">
          {formatValue(numeric)} {unit || ""}
        </span>
      </div>

      <div className={`pg-slider-track-wrap ${showValueBubble ? "pg-slider-track-wrap-with-bubble" : ""}`}>
        {showValueBubble ? (
          <div
            className={`pg-slider-bubble ${isDragging ? "pg-slider-bubble-active" : ""} ${pulse ? "pg-slider-bubble-pulse" : ""}`}
            style={{ left: `${percent}%` }}
          >
            {formatValue(numeric)}
          </div>
        ) : null}

        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={numeric}
          className={`pg-slider ${isDragging ? "pg-slider-dragging" : ""}`}
          style={{
            background: `linear-gradient(90deg, var(--text-1) 0%, var(--navy-light) ${percent}%, rgba(70, 77, 89, 0.86) ${percent}%, rgba(24, 27, 34, 0.92) 100%)`,
          }}
          onMouseDown={() => setIsDragging(true)}
          onMouseUp={() => setIsDragging(false)}
          onTouchStart={() => setIsDragging(true)}
          onTouchEnd={() => setIsDragging(false)}
          onBlur={() => setIsDragging(false)}
          onChange={(e) => handleChange(e.target.value)}
        />
      </div>

      {showTicks && tickValues.length > 1 ? (
        <div className="pg-slider-ticks">
          {tickValues.map((tick, index) => {
            const tickPercent = ((tick - min) / denominator) * 100;
            return (
              <div key={`${tick}-${index}`} className="pg-slider-tick" style={{ left: `${tickPercent}%` }}>
                <span className="pg-slider-tick-line" />
                <span className="pg-slider-tick-label">
                  {tickFormatter ? tickFormatter(tick) : formatValue(tick)}
                </span>
              </div>
            );
          })}
        </div>
      ) : null}

      {showInput ? (
        <input
          className="pg-input mt-2"
          type="number"
          min={min}
          max={max}
          step={step}
          value={numeric}
          onChange={(e) => handleChange(e.target.value || min)}
        />
      ) : null}
    </div>
  );
}
